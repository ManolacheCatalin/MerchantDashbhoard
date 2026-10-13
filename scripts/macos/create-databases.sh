#!/usr/bin/env bash
# Creates the local PostgreSQL databases used by the project ("awin" for the app, "awin_test" for the tests).
# Safe to re-run: databases that already exist are left untouched. Tables are NOT created here;
# Liquibase creates the schema when the app or the tests start.
#
# Usage (from the project root):
#   ./scripts/macos/create-databases.sh
#   ./scripts/macos/create-databases.sh -U postgres -p 5432
#   ./scripts/macos/create-databases.sh awin            # only one of them
#
# Options: -U user (default postgres), -h host (default localhost), -p port (default 5432).
# Password: taken from PGPASSWORD if set; otherwise you are prompted (input is not echoed).
# psql: taken from PATH, or set PSQL=/path/to/psql.

set -euo pipefail

username=postgres
db_host=localhost
port=5432
psql_bin=${PSQL:-psql}

while getopts "U:h:p:" opt; do
	case $opt in
		U) username=$OPTARG ;;
		h) db_host=$OPTARG ;;
		p) port=$OPTARG ;;
		*) echo "Usage: $0 [-U user] [-h host] [-p port] [database ...]" >&2; exit 2 ;;
	esac
done
shift $((OPTIND - 1))
if [[ $# -eq 0 ]]; then
	databases=(awin awin_test)
else
	databases=("$@")
fi

if ! command -v "$psql_bin" >/dev/null 2>&1; then
	echo "psql not found. Install PostgreSQL 17 and add its bin folder to PATH, or set PSQL=/path/to/psql." >&2
	exit 1
fi

if [[ -z "${PGPASSWORD:-}" ]]; then
	read -r -s -p "Password for PostgreSQL user '$username': " PGPASSWORD
	echo >&2
fi
export PGPASSWORD

# Connects to the built-in "postgres" maintenance database; CREATE DATABASE cannot target the database itself.
run_psql() {
	"$psql_bin" -h "$db_host" -p "$port" -U "$username" -d postgres -v ON_ERROR_STOP=1 -tA -c "$1" | tr -d '\r'
}

for name in "${databases[@]}"; do
	# Names are interpolated into SQL, so allow only plain lower-case identifiers.
	if [[ ! $name =~ ^[a-z_][a-z0-9_]*$ ]]; then
		echo "Invalid database name '$name' (use lower-case letters, digits and underscores)." >&2
		exit 1
	fi
	if [[ "$(run_psql "SELECT 1 FROM pg_database WHERE datname = '$name';")" == "1" ]]; then
		echo "Database '$name' already exists - skipped."
	else
		run_psql "CREATE DATABASE $name;" >/dev/null
		echo "Database '$name' created."
	fi
done
