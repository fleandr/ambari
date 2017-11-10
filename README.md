Ambari compile <br>

 mvn -B clean install package jdeb:jdeb -X -DskipTests -Drat.ignoreErrors=true  -Dpython.ver="python >= 2.6" -Preplaceurl


