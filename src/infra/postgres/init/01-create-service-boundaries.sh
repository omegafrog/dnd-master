#!/bin/sh
set -eu

create_boundary() {
  database="$1"
  role="$2"
  schema="$3"
  password="$4"

  psql --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" --set ON_ERROR_STOP=1 <<-SQL
    CREATE ROLE ${role} LOGIN PASSWORD '${password}' NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT;
    CREATE DATABASE ${database} OWNER ${role};
    REVOKE ALL ON DATABASE ${database} FROM PUBLIC;
    GRANT CONNECT, TEMPORARY ON DATABASE ${database} TO ${role};
SQL

  psql --username "$POSTGRES_USER" --dbname "$database" --set ON_ERROR_STOP=1 <<-SQL
    REVOKE CREATE ON SCHEMA public FROM PUBLIC;
    CREATE SCHEMA ${schema} AUTHORIZATION ${role};
    ALTER ROLE ${role} IN DATABASE ${database} SET search_path TO ${schema};
SQL
}

create_boundary identity_access identity_access_app identity_access identity-access-local
create_boundary adventure adventure_app adventure adventure-local
create_boundary rule_knowledge rule_knowledge_app rule_knowledge rule-knowledge-local
create_boundary character_management character_management_app character_management character-management-local
create_boundary dice_roll dice_roll_app dice_roll dice-roll-local
create_boundary combat_map combat_map_app combat_map combat-map-local
create_boundary ai_game_master ai_game_master_app ai_game_master ai-game-master-local

psql --username "$POSTGRES_USER" --dbname rule_knowledge --set ON_ERROR_STOP=1 <<-SQL
  CREATE EXTENSION IF NOT EXISTS vector;
SQL
