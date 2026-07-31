"""FastAPI生产装配入口；Nginx直接代理到这里，不经过Java。"""

from __future__ import annotations

from redis.asyncio import Redis
from sqlalchemy import text
from sqlalchemy.ext.asyncio import create_async_engine

from testagent_workflow.api import AppDependencies, create_app
from testagent_workflow.auth import RedisTokenAuthenticator
from testagent_workflow.database import PostgresWorkflowStore
from testagent_workflow.intent import PlatformGrantedIntentClassifier
from testagent_workflow.impact_engine import ImpactGraphDependencies
from testagent_workflow.platform import PlatformCapabilityClient, PlatformRequestIdentity
from testagent_workflow.reports import (
    GrantBackedResultSynthesizer,
    PlatformGrantedReportQuestionAnswerer,
    StoreReportPublisher,
)
from testagent_workflow.registry import WorkflowRegistry
from testagent_workflow.runner_client import (
    PlatformAuthorizationAdapter,
    ReportFollowupRetentionService,
    RemoteRunnerAdapter,
    RunCancellationService,
    RunGrantManager,
    RunnerAnalyzerAdapter,
    RunnerApiClient,
    StoreProgressPublisher,
)
from testagent_workflow.settings import WorkflowSettings
from testagent_workflow.worker import WorkflowWorker, WorkspaceCleanupService
from testagent_workflow.workflows.impact_analysis import impact_analysis_definition


def build_app(settings: WorkflowSettings | None = None):  # type: ignore[no-untyped-def]
    configuration = settings or WorkflowSettings()  # type: ignore[call-arg]
    redis = Redis.from_url(
        configuration.redis_url.get_secret_value(),
        decode_responses=True,
    )
    engine = create_async_engine(
        configuration.database_url,
        pool_pre_ping=True,
        pool_size=10,
        max_overflow=20,
    )
    store = PostgresWorkflowStore(engine)
    registry = WorkflowRegistry([impact_analysis_definition()])
    platform = PlatformCapabilityClient(
        configuration.platform_base_url,
        configuration.platform_hmac_secret.get_secret_value().encode(),
    )
    runner = RunnerApiClient(
        configuration.runner_base_url,
        configuration.runner_id,
        configuration.runner_hmac_secret.get_secret_value().encode(),
    )

    async def readiness() -> dict[str, str]:
        await redis.ping()
        async with engine.connect() as connection:
            await connection.execute(text("SELECT 1"))
        return {"status": "UP", "database": "UP", "authenticationRedis": "UP"}

    app = create_app(
        AppDependencies(
            authenticator=RedisTokenAuthenticator(redis),
            store=store,
            registry=registry,
            classifier=PlatformGrantedIntentClassifier(
                registry,
                platform,
                model_name=configuration.intent_model_name,
            ),
            platform=platform,
            cancellation_service=RunCancellationService(platform, runner),
            question_answerer=PlatformGrantedReportQuestionAnswerer(
                platform,
                configuration.report_qa_model_name,
            ),
            followup_retention=ReportFollowupRetentionService(
                store,
                runner,
                retention_hours=configuration.workspace_retention_hours,
            ),
            readiness_probe=readiness,
            shutdown_callbacks=(runner.aclose, platform.aclose, redis.aclose, engine.dispose),
        )
    )
    app.state.settings = configuration
    app.state.database_engine = engine
    app.state.redis = redis
    return app


def build_worker(
    checkpointer: object,
    settings: WorkflowSettings | None = None,
) -> WorkflowWorker:
    """装配独立Worker进程；API进程不运行Docker或任务循环。"""

    configuration = settings or WorkflowSettings()  # type: ignore[call-arg]
    engine = create_async_engine(
        configuration.database_url,
        pool_pre_ping=True,
        pool_size=5,
        max_overflow=10,
    )
    store = PostgresWorkflowStore(engine)
    registry = WorkflowRegistry([impact_analysis_definition()])
    platform = PlatformCapabilityClient(
        configuration.platform_base_url,
        configuration.platform_hmac_secret.get_secret_value().encode(),
    )
    runner = RunnerApiClient(
        configuration.runner_base_url,
        configuration.runner_id,
        configuration.runner_hmac_secret.get_secret_value().encode(),
    )

    def dependencies(run):  # type: ignore[no-untyped-def]
        identity = PlatformRequestIdentity(run.owner_user_id, run.session_digest)
        analyzer_ids = list(run.input_data.get("analyzerIds", ["codex"]))
        grants = RunGrantManager(
            platform,
            identity,
            run.task_id,
            run.id,
            [*analyzer_ids, "agentscope-synthesis"],
        )
        return ImpactGraphDependencies(
            platform=PlatformAuthorizationAdapter(platform),
            runner=RemoteRunnerAdapter(
                platform,
                runner,
                configuration.resolved_runner_public_key(),
                store,
                grants,
                retention_hours=configuration.workspace_retention_hours,
            ),
            analyzers=RunnerAnalyzerAdapter(
                runner,
                grants,
                model_name=configuration.analysis_model_name,
            ),
            synthesizer=GrantBackedResultSynthesizer(
                grants,
                configuration.synthesis_model_name,
                store,
            ),
            reports=StoreReportPublisher(store),
            progress=StoreProgressPublisher(store),
        )

    worker = WorkflowWorker(
        configuration.worker_id,
        store,
        registry,
        dependencies,
        checkpointer=checkpointer,
        lease_seconds=configuration.worker_lease_seconds,
    )
    worker.workspace_cleanup = WorkspaceCleanupService(store, runner)  # type: ignore[attr-defined]
    worker.resources = (engine, platform, runner)  # type: ignore[attr-defined]
    return worker
