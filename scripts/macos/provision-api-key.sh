#!/usr/bin/env bash
# Creates (or replaces) the demo API key for one merchant in the local "awin" database.
#
# - Generates a random 256-bit key (URL-safe Base64, no padding).
# - Stores ONLY its lower-case hex SHA-256 hash in merchant_api_keys, hashed exactly as the app does
#   (SHA-256 over the key's UTF-8 text). The plain key is never written to disk or the database.
# - Each merchant has one key: an existing key for the merchant is replaced and re-activated.
# - The plain key is the only thing written to standard output, once. It cannot be recovered from the hash;
#   if you lose it, run the script again to replace it.
#
# Run after the app has started at least once (Liquibase must have created the tables and demo merchants).
#
# Usage (from the project root):
#   API_KEY=$(./scripts/macos/provision-api-key.sh 1)     # keep the key in $API_KEY for requests
#   ./scripts/macos/provision-api-key.sh 2                # just print it
#
# Options (before the merchant id): -d database (default awin), -U user (default postgres),
# -h host (default localhost), -p port (default 5432).
# Password: taken from PGPASSWORD if set; otherwise you are prompted (input is not echoed).
# psql: taken from PATH, or set PSQL=/path/to/psql.

set -euo pipefail

database=awin
username=postgres
db_host=localhost
port=5432
psql_bin=${PSQL:-psql}

usage() { echo "Usage: $0 [-d database] [-U user] [-h host] [-p port] <merchant-id>" >&2; exit 2; }

while getopts "d:U:h:p:" opt; do
	case $opt in
		d) database=$OPTARG ;;
		U) username=$OPTARG ;;
		h) db_host=$OPTARG ;;
		p) port=$OPTARG ;;
		*) usage ;;
	esac
done
shift $((OPTIND - 1))
[[ $# -eq 1 ]] || usage
merchant_id=$1

# Both values are interpolated into SQL, so validate them strictly.
[[ $merchant_id =~ ^[0-9]+$ ]] || { echo "Merchant id must be a number." >&2; exit 1; }
[[ $database =~ ^[a-z_][a-z0-9_]*$ ]] || { echo "Invalid database name '$database'." >&2; exit 1; }

if ! command -v "$psql_bin" >/dev/null 2>&1; then
	echo "psql not found. Install PostgreSQL 17 and add its bin folder to PATH, or set PSQL=/path/to/psql." >&2
	exit 1
fi

if [[ -z "${PGPASSWORD:-}" ]]; then
	read -r -s -p "Password for PostgreSQL user '$username': " PGPASSWORD
	echo >&2
fi
export PGPASSWORD

run_psql() {
	"$psql_bin" -h "$db_host" -p "$port" -U "$username" -d "$database" -v ON_ERROR_STOP=1 -tA -c "$1" | tr -d '\r'
}

merchant_name=$(run_psql "SELECT name FROM merchants WHERE id = $merchant_id;")
if [[ -z $merchant_name ]]; then
	echo "Merchant $merchant_id does not exist in database '$database'. Start the app once so the demo data is loaded, or pick another id." >&2
	exit 1
fi

# 1. Random 256-bit key, URL-safe Base64 without padding (43 characters)
api_key=$(openssl rand -base64 32 | tr '+/' '-_' | tr -d '=\r\n')

# 2. Hash exactly as the app does: SHA-256 over the key text, lower-case hex (last field of openssl's output)
key_hash=$(printf '%s' "$api_key" | openssl dgst -sha256 | awk '{print $NF}' | tr -d '\r')

# 3. Store only the hash (insert, or replace and re-activate the merchant's existing key)
run_psql "INSERT INTO merchant_api_keys (merchant_id, key_hash) VALUES ($merchant_id, '$key_hash')
ON CONFLICT (merchant_id) DO UPDATE SET key_hash = EXCLUDED.key_hash, active = TRUE, created_at = now();" >/dev/null

echo "API key stored for merchant $merchant_id ($merchant_name) in database '$database'. Keep it now; it is shown only once:" >&2
# 4. Print the plain key on standard output
printf '%s\n' "$api_key"
