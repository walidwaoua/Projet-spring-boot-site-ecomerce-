SELECT format('CREATE DATABASE %I', database_name)
FROM (VALUES
    ('service_commande'),
    ('service_stock'),
    ('service_notification'),
    ('service_catalogue'),
    ('service_utilisateurs')
) AS expected_databases(database_name)
WHERE NOT EXISTS (
    SELECT 1 FROM pg_database WHERE datname = database_name
)
\gexec
