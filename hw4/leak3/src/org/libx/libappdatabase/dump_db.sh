#!/bin/sh

java -Duser.home=../../../../WEB-INF -cp "../../../../WEB-INF/lib/*" org.basex.BaseX -v queries/results_query.xq
