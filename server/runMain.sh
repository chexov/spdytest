set -xue
main=$1; shift;
export LD_LIBRARY_PATH=/opt/live/lib
mvn exec:java -Dexec.mainClass="$main" -Dexec.args="$*"


