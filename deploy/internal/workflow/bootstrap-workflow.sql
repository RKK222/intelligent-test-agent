\set ON_ERROR_STOP on

-- Usage (run as a PostgreSQL administrator without putting passwords on the command line):
-- psql -v workflow_owner_password="..." -v workflow_runtime_password="..." -f bootstrap-workflow.sql
\if :{?workflow_owner_password}
\else
  \echo 'workflow_owner_password is required'
  \quit 2
\endif
\if :{?workflow_runtime_password}
\else
  \echo 'workflow_runtime_password is required'
  \quit 2
\endif

SELECT format(
  'CREATE ROLE test_agent_workflow_owner LOGIN PASSWORD %L NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION',
  :'workflow_owner_password'
)
WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'test_agent_workflow_owner')
\gexec

SELECT format(
  'CREATE ROLE test_agent_workflow LOGIN PASSWORD %L NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION',
  :'workflow_runtime_password'
)
WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'test_agent_workflow')
\gexec

ALTER ROLE test_agent_workflow_owner PASSWORD :'workflow_owner_password';
ALTER ROLE test_agent_workflow PASSWORD :'workflow_runtime_password';

SELECT 'CREATE DATABASE test_agent_workflow OWNER test_agent_workflow_owner'
WHERE NOT EXISTS (SELECT 1 FROM pg_database WHERE datname = 'test_agent_workflow')
\gexec

REVOKE ALL ON DATABASE test_agent_workflow FROM PUBLIC;
GRANT ALL ON DATABASE test_agent_workflow TO test_agent_workflow_owner;
GRANT CONNECT ON DATABASE test_agent_workflow TO test_agent_workflow;

\connect test_agent_workflow

REVOKE CREATE ON SCHEMA public FROM PUBLIC;
GRANT ALL ON SCHEMA public TO test_agent_workflow_owner;
GRANT USAGE ON SCHEMA public TO test_agent_workflow;

ALTER DEFAULT PRIVILEGES FOR ROLE test_agent_workflow_owner IN SCHEMA public
  GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO test_agent_workflow;
ALTER DEFAULT PRIVILEGES FOR ROLE test_agent_workflow_owner IN SCHEMA public
  GRANT USAGE, SELECT ON SEQUENCES TO test_agent_workflow;

GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO test_agent_workflow;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO test_agent_workflow;
