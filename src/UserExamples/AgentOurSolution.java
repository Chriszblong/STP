package UserExamples;

import COMSETsystem.BaseAgent;
import COMSETsystem.CityMap;
import COMSETsystem.Intersection;
import COMSETsystem.LocationOnRoad;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Random;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

public class AgentOurSolution extends BaseAgent {

    // search route stored as a list of intersections.
    LinkedList<Intersection> route = new LinkedList<Intersection>();

    // random number generator
    Random rnd;

    // a static singleton object of a data model, shared by all agents
    static IntellgentDataModel dataModel = null;

    /**
     * AgentOurSolution constructor.
     *
     * @param id An id that is unique among all agents and resources
     * @param map The map
     */
    public AgentOurSolution(long id, CityMap map) {
        super(id, map);
        rnd = new Random(id);
        if (dataModel == null) {
            dataModel = new IntellgentDataModel(map);
        }
    }

    /**
     *
     * @param currentLocation
     * @param currentTime
     */
    @Override
    public void planSearchRoute(LocationOnRoad currentLocation, long currentTime) {
        route.clear();
        Intersection sourceIntersection = currentLocation.road.to;
        Set<Integer> ReachableClusters;
        boolean isWeekday = dataModel.isWeekday(currentTime);
        int rank = dataModel.getPopularityRank(currentLocation,currentTime);
        if(isWeekday){ // if today is weekday
            if(dataModel.isPeakHours(currentTime)){
                if (rank <= 10) {
                    Intersection destinationIntersection = dataModel.getRandomIntersection(currentLocation);
                    // destination cannot be the source
                    // if destination is the source, selelct a intersection again from current zone
                    while (sourceIntersection.id == destinationIntersection.id)
                        destinationIntersection = dataModel.getRandomIntersection(currentLocation);
                    route = map.shortestTravelTimePath(sourceIntersection, destinationIntersection);
                    route.poll();
                    return;
                } else if (rank > 140)
                    ReachableClusters = dataModel.getReachableClusters(currentLocation, currentTime, 600, 1200);
                else if (rank <= 30)
                    ReachableClusters = dataModel.getReachableClusters(currentLocation, currentTime, 0, 600);
                else if (rank <= 60)
                    ReachableClusters = dataModel.getReachableClusters(currentLocation, currentTime, 300, 600);
                else
                    ReachableClusters = dataModel.getReachableClusters(currentLocation, currentTime, 600, 900);
            }else {
                if (rank <= 5) {
                    Intersection destinationIntersection = dataModel.getRandomIntersection(currentLocation);
                    while (sourceIntersection.id == destinationIntersection.id)
                        destinationIntersection = dataModel.getRandomIntersection(currentLocation);
                    route = map.shortestTravelTimePath(sourceIntersection, destinationIntersection);
                    route.poll();
                    return;
                }
                else if (rank > 120)
                    ReachableClusters = dataModel.getReachableClusters(currentLocation, currentTime, 1500, 2100);
                else if (rank <= 30)
                    ReachableClusters = dataModel.getReachableClusters(currentLocation, currentTime, 0, 600);
                else if (rank <= 60)
                    ReachableClusters = dataModel.getReachableClusters(currentLocation, currentTime, 600, 1200);
                else
                    ReachableClusters = dataModel.getReachableClusters(currentLocation, currentTime, 1200, 1800);
            }
        }else {
            //if today is weekend
            if(dataModel.isPeakHours(currentTime)){
                if (rank <= 10) {
                    Intersection destinationIntersection = dataModel.getRandomIntersection(currentLocation);
                    while (sourceIntersection.id == destinationIntersection.id)
                        destinationIntersection = dataModel.getRandomIntersection(currentLocation);
                    route = map.shortestTravelTimePath(sourceIntersection, destinationIntersection);
                    route.poll();
                    return;
                } else if (rank > 120)
                    ReachableClusters = dataModel.getReachableClusters(currentLocation, currentTime, 900, 1500);
                else if (rank <= 30)
                    ReachableClusters = dataModel.getReachableClusters(currentLocation, currentTime, 0, 600);
                else if (rank <= 60)
                    ReachableClusters = dataModel.getReachableClusters(currentLocation, currentTime, 300, 900);
                else
                    ReachableClusters = dataModel.getReachableClusters(currentLocation, currentTime, 600, 1200);
            }else{
                if (rank <= 5) {
                    Intersection destinationIntersection = dataModel.getRandomIntersection(currentLocation);
                    while (sourceIntersection.id == destinationIntersection.id)
                        destinationIntersection = dataModel.getRandomIntersection(currentLocation);
                    route = map.shortestTravelTimePath(sourceIntersection, destinationIntersection);
                    route.poll();
                    return;
                }
                else if (rank > 120)
                    ReachableClusters = dataModel.getReachableClusters(currentLocation, currentTime, 1500, 2100);
                else if (rank <= 30)
                    ReachableClusters = dataModel.getReachableClusters(currentLocation, currentTime, 0, 600);
                else if (rank <= 60)
                    ReachableClusters = dataModel.getReachableClusters(currentLocation, currentTime, 600, 1200);
                else
                    ReachableClusters = dataModel.getReachableClusters(currentLocation, currentTime, 1200, 1800);
            }
        }
        // if there are no cluster that meet the conditions
        // select the destination intersection in the current zone
        if(ReachableClusters.size()==0){
            Intersection destinationIntersection = dataModel.getRandomIntersection(currentLocation);
            while (sourceIntersection.id == destinationIntersection.id)
                destinationIntersection = dataModel.getRandomIntersection(currentLocation);
            route = map.shortestTravelTimePath(sourceIntersection,destinationIntersection);
            route.poll();
            return;
        }
        Integer[] clustersArray = ReachableClusters.toArray(new Integer[ReachableClusters.size()]);

        // choose zone according to probability
        int clusterIndex = 0;
        ArrayList<Double> clusterWeight = new ArrayList<>();
        double sumWeight = 0.0;
        for(int i=0;i<clustersArray.length;i++){
            double weight = dataModel.getClusterWeight(clustersArray[i],currentTime);
            clusterWeight.add(weight);
            sumWeight += weight;
        }
        if(sumWeight<0.0001){
            //All the cluster weights are 0, then select destination randomly
            clusterIndex = clustersArray[rnd.nextInt(clustersArray.length)];
        }else{
            double k = rnd.nextDouble();
            double kbound = 0.0;
            for(int i=0;i<clusterWeight.size();i++){
                kbound += clusterWeight.get(i);
                double bound = kbound/sumWeight;
                if(k<bound){
                    clusterIndex = clustersArray[i];
                    break;
                }
            }
        }
        Intersection destinationIntersection = dataModel.getCenterIntersection(clusterIndex,currentTime);
        if(sourceIntersection.id == destinationIntersection.id){
            while (sourceIntersection.id == destinationIntersection.id)
                destinationIntersection = dataModel.getRandomIntersection(clusterIndex);
        }

        route = map.shortestTravelTimePath(sourceIntersection,destinationIntersection);
        route.poll();
    }

    /**
     *
     * @param currentLocation The agent's location at the time when the method is called
     * @param currentTime The time at which the method is invoked
     * @return
     */
    @Override
    public Intersection nextIntersection(LocationOnRoad currentLocation, long currentTime) {
        if (route.size() != 0) {
            // Route is not empty, take the next intersection.
            Intersection nextIntersection = route.poll();
            return nextIntersection;
        } else {
            // Finished the planned route. Plan a new route.
            planSearchRoute(currentLocation, currentTime);
            return route.poll();
        }
    }

    /**
     *
     * @param currentLocation The agent's location at the time when the method is called
     * @param currentTime The time at which the assignment occurs
     * @param resourceId The id of the resource to which the agent is assigned
     * @param resourcePickupLocation The pickup location of the resource to which the agent is assigned
     * @param resourceDropoffLocation The dropoff location of the resource to which the agent is assigned
     */
    @Override
    public void assignedTo(LocationOnRoad currentLocation, long currentTime, long resourceId, LocationOnRoad resourcePickupLocation, LocationOnRoad resourceDropoffLocation) {
        // Clear the current route.
        route.clear();

        Logger.getLogger(this.getClass().getName()).log(Level.INFO, "Agent " + this.id + " assigned to resource " + resourceId);
        Logger.getLogger(this.getClass().getName()).log(Level.INFO, "currentLocation = " + currentLocation);
        Logger.getLogger(this.getClass().getName()).log(Level.INFO, "currentTime = " + currentTime);
        Logger.getLogger(this.getClass().getName()).log(Level.INFO, "resourcePickupLocation = " + resourcePickupLocation);
        Logger.getLogger(this.getClass().getName()).log(Level.INFO, "resourceDropoffLocation = " + resourceDropoffLocation);
    }
}
