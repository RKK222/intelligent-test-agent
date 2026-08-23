alter table local_client_release_artifacts drop constraint ck_local_client_release_artifacts_kind;
alter table local_client_release_artifacts add constraint ck_local_client_release_artifacts_kind check (
    artifact_kind in ('CLIENT_JAR', 'JDK', 'OPENCODE', 'PUBLIC_CAPABILITIES')
);
