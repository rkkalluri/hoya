<!---
  Licensed under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License.
  You may obtain a copy of the License at
  
   http://www.apache.org/licenses/LICENSE-2.0
  
  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License. See accompanying LICENSE file.
-->
  
# Examples

 
## Setup
 
### Setting up a YARN cluster
 
For simple local demos, a Hadoop pseudo-distributed cluster will suffice -if on a VM then
its configuration should be changed to use a public (machine public) IP.



# preamble

    export HADOOP_CONF_DIR=/home/stevel/conf
    export PATH=/home/stevel/hadoop/bin:/home/stevel/hadoop/sbin:~/zookeeper-3.4.5/bin:$PATH
    
    hdfs namenode -format ubuntu
  


## get hbase in

copy to local 

    hbase-0.95.3-SNAPSHOT-bin.tar 


    hdfs dfs -rm hdfs://ubuntu:9000/hbase.tar
    hdfs dfs -copyFromLocal hbase-0.95.3-SNAPSHOT-bin.tar hdfs://ubuntu:9000/hbase.tar
    hdfs dfs -ls hdfs://ubuntu:9000/

# start all the services

    hadoop-daemon.sh --config $HADOOP_CONF_DIR --script hdfs start namenode
    hadoop-daemon.sh --config $HADOOP_CONF_DIR --script hdfs start datanode
    
    yarn-daemon.sh --config $HADOOP_CONF_DIR start resourcemanager
    yarn-daemon.sh --config $HADOOP_CONF_DIR start nodemanager
    
    zookeeper-3.4.5/bin/zkServer.sh start
    
    
# stop them

    hadoop-daemon.sh --config $HADOOP_CONF_DIR --script hdfs stop namenode
    hadoop-daemon.sh --config $HADOOP_CONF_DIR --script hdfs stop datanode
    
    yarn-daemon.sh --config $HADOOP_CONF_DIR stop resourcemanager
    yarn-daemon.sh --config $HADOOP_CONF_DIR stop nodemanager
    


NN up on [http://ubuntu:50070/dfshealth.jsp](http://ubuntu:50070/dfshealth.jsp)
RM yarn-daemon.sh --config $HADOOP_CONF_DIR start nodemanager

    zookeeper-3.4.5/bin/zkServer.sh start


    # shutdown
    ./zookeeper-3.4.5/bin/zkServer.sh stop


    # FS health
    
 

 ## Create a Hoya Cluster
 
 
    java -jar target/hoya-0.3-SNAPSHOT.jar org.apache.hadoop.hoya.Hoya create cl1 --workers 1 --manager ubuntu:8032 --filesystem hdfs://ubuntu:9000 --zkhosts localhost --image hdfs://ubuntu:9000/hbase.tar
    
    # create the cluster
    java -jar target/hoya-0.3-SNAPSHOT.jar org.apache.hadoop.hoya.Hoya\
      create cl1 --workers 1\
      --manager ubuntu:8032 --filesystem hdfs://ubuntu:9000 --zkhosts localhost \
      --image hdfs://ubuntu:9000/hbase.tar \
      --confdir file:////Users/stevel/Projects/Hortonworks/Projects/hoya/src/test/configs/ubuntu/hbase \
      --masterinfoport 8080 --masterheap 128 \
      --roleopt master env.MALLOC_ARENA_MAX 4 \
      --workerinfoport 8081 --workerheap 128 

    # freeze the cluster
    java -jar target/hoya-0.3-SNAPSHOT.jar org.apache.hadoop.hoya.Hoya\
    freeze cl1 \
    --manager ubuntu:8032 --filesystem hdfs://ubuntu:9000

    # thaw a cluster
    java -jar target/hoya-0.3-SNAPSHOT.jar org.apache.hadoop.hoya.Hoya \
    thaw cl1 \
    --manager ubuntu:8032 --filesystem hdfs://ubuntu:9000

    # destroy the cluster
    java -jar target/hoya-0.3-SNAPSHOT.jar org.apache.hadoop.hoya.Hoya\
    destroy cl1 \
    --manager ubuntu:8032 --filesystem hdfs://ubuntu:9000

    # list clusters
    java -jar target/hoya-0.3-SNAPSHOT.jar org.apache.hadoop.hoya.Hoya\
    list cl1 \
    --manager ubuntu:8032 --filesystem hdfs://ubuntu:9000
    
    
    
      