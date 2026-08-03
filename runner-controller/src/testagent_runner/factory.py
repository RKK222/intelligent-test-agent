"""Runner生产装配。"""

from testagent_runner.analyzer_executor import DockerAnalyzerExecutor
from testagent_runner.api import create_runner_app
from testagent_runner.credentials import RunnerCredentialDecryptor
from testagent_runner.docker_runtime import AnalysisNetworkPolicy, DockerRuntime
from testagent_runner.security import RunnerHmacAuthenticator, SqliteNonceStore
from testagent_runner.service import RunnerService
from testagent_runner.settings import RunnerSettings
from testagent_runner.tickets import CheckoutTicketClient


def build_app(settings: RunnerSettings | None = None):  # type: ignore[no-untyped-def]
    configuration = settings or RunnerSettings()  # type: ignore[call-arg]
    docker = DockerRuntime(
        network_policy=AnalysisNetworkPolicy(
            network=configuration.analysis_network,
            subnet=configuration.analysis_network_subnet,
            model_gateway_cidr=configuration.model_gateway_cidr,
            model_gateway_port=configuration.model_gateway_port,
        )
    )
    credentials = RunnerCredentialDecryptor(
        configuration.private_key_path,
        configuration.credential_root,
        require_tmpfs=True,
    )
    tickets = CheckoutTicketClient(
        configuration.platform_base_url,
        configuration.runner_id,
        configuration.platform_hmac_secret.get_secret_value().encode(),
    )
    service = RunnerService(
        configuration,
        tickets,
        credentials,
        docker,
        DockerAnalyzerExecutor(docker),
    )
    authenticator = RunnerHmacAuthenticator(
        configuration.worker_hmac_secret.get_secret_value().encode(),
        configuration.runner_id,
        SqliteNonceStore(configuration.root / ".runner-nonces.sqlite"),
    )
    return create_runner_app(
        service,
        authenticator,
        public_key_pem=credentials.public_key_pem(),
    )
