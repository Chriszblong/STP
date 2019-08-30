# An Effective Partitioning Approach for Competitive Spatial-Temporal Searching (GIS Cup)

## Description

This is a submittion called STP to 2019 SIGSPATIAL GIS CUP.

## Solution Overview

The UserExamples.AgentOurSolution is our solution implemented COMSETsystem.BaseAgent and the UserExamples.IntellgentDataModel is a data model that represents resource availability pattern to help agent search for resource. The overall logic of our solution is as follow:
1. We take 7 days from the historical data for K-means clustering, and then divide each intersection and road into the nearest cluster center. Thus each cluster forms a spatial zone.
2. Then we divide a day into several time slots and collect the pick-up and drop-off data in different time slot within each spatial zone. We process the data to generate a data model and use it to assist our future routing strategy.
3. Considering the difference in pick-up and drop-off information for different spatial zones in different time periods, we define the zone weight for each zone in every time periods. An empty agent chooses destiantion zone according to the zone weights and travels to the destination along the shortest travel time path. This procedure is repeated until the agent is assigned to a resource.

## Installing, building, and running STP

COMSET is a simulator described in the <a href="https://sigspatial2019.sigspatial.org/giscup2019/problem"> 2019 SIGSPATIAL GIS CUP</a>. COMSET simulates crowdsourced taxicabs (called <i>agents</i>) searching for customers (called <i>resources</i>) to pick up in a city. 

To install , unzip COMSET.zip, which includes our source code and all dependencies.

The main class is Main. The configurable system parameters are defined in etc/config.properties.
Besides, there are some configurable system parameters that need to be adjusted. Before building the project, it's necessary to download the training dataset and set mysolution.training_dataset to point to it. 
For example:
comset.dataset_file = datasets/yellow_tripdata_2016-06-01_busyhours.csv
mysolution.training_dataset = datasets/yellow_tripdata_2016-06.csv 

mysolution.training_dataset is the training dataset file. This file is downloaded from New York TLC Trip Record YELLOW Data, which includes records of  both pick-up and drop-off are within New York.
comset.dataset_file is the testing dataset file, which includes the data of the chosen day starting from 8am until 9pm.

The project can be run with mvn as follows: 
1. Run "mvn install" or "mvn package" to build.
2. mvn exec:java -Dexec.mainClass="Main"

With the configuration file coming up with the system, the above command will run simulation on the Manhattan road network with 5000 agents using our search strategy. The resources are the trip records for June 1st, 2016 starting from 8:00am until 10:00pm. The simulation should be finished in a few minutes, and you should get something like the following:

- average agent search time: 460 seconds
- average resource wait time: 239 seconds
- resource expiration percentage: 11%


Note the results of each run may change due to different clustering results.

## Citing

Please cite our paper if you choose to use our code.


