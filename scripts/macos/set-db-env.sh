# Sets the database credentials used by the app (database "awin") and the tests (database "awin_test")
# as environment variables for the CURRENT terminal session only. Nothing is written to disk.
#
# Must be sourced (not executed), so the variables stay in your shell. Works in zsh (the macOS default) and bash:
#   source scripts/macos/set-db-env.sh
#   source scripts/macos/set-db-env.sh other_user other_test_user
#
# Then run from the same terminal, e.g.:
#   ./gradlew bootRun
#   ./gradlew test

_awin_user=${1:-postgres}
_awin_test_user=${2:-$_awin_user}

export DB_USERNAME="$_awin_user"
printf "Password for '%s' on database 'awin': " "$_awin_user"
read -r -s DB_PASSWORD
echo
export DB_PASSWORD

export TEST_DB_USERNAME="$_awin_test_user"
printf "Password for '%s' on database 'awin_test' (Enter to reuse the previous one): " "$_awin_test_user"
read -r -s TEST_DB_PASSWORD
echo
if [ -z "$TEST_DB_PASSWORD" ]; then
	TEST_DB_PASSWORD="$DB_PASSWORD"
fi
export TEST_DB_PASSWORD

echo "Set DB_USERNAME=$DB_USERNAME and TEST_DB_USERNAME=$TEST_DB_USERNAME (passwords set, not shown)."
unset _awin_user _awin_test_user
