package UserExamples;

import COMSETsystem.*;
import DataParsing.CSVNewYorkParser;
import DataParsing.Resource;
import MapCreation.MapCreator;

import java.io.File;
import java.io.FileInputStream;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class IntellgentDataModel {
    // A reference to the map.
    CityMap map;

    // ZoneId
    ZoneId zone;

    // Number of clusters
    int clusterNum;

    // The length of time slot,in minutes
    int timeSlotLength;

    // delta time = K*timeSlotLength
    int K;

    // The path of training dataset
    String trainDataset;

    // The result of clustering
    List<List<Intersection>> intersectionClusters;
    List<List<Road>> roadClusters;

    List<Intersection> nonPeakCenterIntersections; // The center intersection of peak hours
    List<Intersection> peakCenterIntersections; // The center intersection of non-peak hours

    // index set of days
    Set<Integer> daySet = new HashSet<>();
    Set<Integer> weekdaySet = new HashSet<>();
    Set<Integer> weekendSet = new HashSet<>();

    //Pick up and drop off points statistics table
    //Zero for weekday and one for weekend
    Map<Integer,Map<Integer,ArrayList<Double>>> clusterAveragePickup = new HashMap<>();
    Map<Integer,Map<Integer,ArrayList<Double>>> clusterAverageDropoff = new HashMap<>();

    //Count zone weights every hour to get zone popularity rank.
    Map<Integer,Map<Integer, Map<Integer,Double>>> zoneTimeperiodPickup = new HashMap<>();
    Map<Integer,Map<Integer, Map<Integer,Double>>> zoneTimeperiodDropoff = new HashMap<>();
    Map<Integer,Map<Integer,ArrayList<Integer>>> zonesPopularityRank = new HashMap<>();

    public IntellgentDataModel(CityMap map) {
        this.map = map;
        this.zone = map.computeZoneId();
        getProperties();
        train();
    }

    /**
     * Get property value from config.properties
     */
    private void getProperties(){
        String configFile = "etc/config.properties";
        try {
            Properties prop = new Properties();
            prop.load(new FileInputStream(configFile));

            //get the property values
            String timeSlotArg = prop.getProperty("mysolution.timeslot_length").trim();
            if (timeSlotArg != null) {
                timeSlotLength = Integer.parseInt(timeSlotArg);
            } else {
                System.out.println("The time slot must be specified the configuration file.");
                System.exit(1);
            }

            String clusterNumArg = prop.getProperty("mysolution.cluster_num").trim();
            if(clusterNumArg != null){
                clusterNum = Integer.parseInt(clusterNumArg);
            }else{
                System.out.println("The number of clusters must be specified the configuration file.");
                System.exit(1);
            }

            trainDataset = prop.getProperty("mysolution.training_dataset").trim();
            if (trainDataset == null) {
                System.out.println("The training dataset file must be specified in the configuration file.");
                System.exit(1);
            }

            String KArg = prop.getProperty("mysolution.K").trim();
            if(KArg != null){
                K = Integer.parseInt(KArg);
            }else{
                System.out.println("The parameter K must be specified the configuration file.");
                System.exit(1);
            }

        } catch (Exception ex) {
            ex.printStackTrace();
        }

    }

    /**
     * Clustering to divide spatial zone and statistic for each zone
     */
    public void train(){
        System.out.println("Training the data model...");
        clustering();
        statisticResources();
    }

    /**
     * @param clusterIndex the index of zone
     * @param currentTime current time
     * @return the weight of corresponding zone at current time
     */
    public double getClusterWeight(int clusterIndex, long currentTime){
        double weight = 0.0;
        int timeSlot = calTimeSlot(currentTime), timeSlotNum = 24*60/timeSlotLength;
        boolean isWeekday = isWeekday(currentTime);
        for(int i=timeSlot;i<timeSlot+K;i++) {
            if(i<timeSlotNum){
                if(isWeekday){
                    weight += clusterAveragePickup.get(0).get(clusterIndex).get(i) - 0.5*clusterAverageDropoff.get(0).get(clusterIndex).get(i);
                }else{
                    weight += clusterAveragePickup.get(1).get(clusterIndex).get(i) - 0.5*clusterAverageDropoff.get(1).get(clusterIndex).get(i);
                }
            }
        }
        if(weight < 0)
            return 0.0;
        return weight;
    }

    /**
     *
     * @param currentLocation
     * @param currentTime
     * @param minCostTime
     * @param maxCostTime
     * @return The region that can be reached in the time from minCostTime to maxCostTime
     */
    public Set<Integer> getReachableClusters(LocationOnRoad currentLocation, long currentTime, long minCostTime, long maxCostTime){
        Set<Integer> clusterSet = new HashSet<>();
        int size;
        if(isPeakHours(currentTime))
            size = peakCenterIntersections.size();
        else
            size = nonPeakCenterIntersections.size();
        for(int i=0;i<size;i++){
            long costTime;
            if(isPeakHours(currentTime))
                costTime = shortestTimeBetween(currentLocation,peakCenterIntersections.get(i));
            else
                costTime = shortestTimeBetween(currentLocation,nonPeakCenterIntersections.get(i));
            if(costTime <= maxCostTime && costTime >= minCostTime)
                clusterSet.add(i);
        }
        return clusterSet;
    }

    public long shortestTimeBetween(LocationOnRoad currentLocation, Intersection destinationIntersection){
        return currentLocation.road.travelTime - currentLocation.travelTimeFromStartIntersection + map.travelTimeBetween(currentLocation.road.to,destinationIntersection);
    }

    /**
     * Get the popularity ranking of the corresponding zone of the currentLocation and currentTime
     * @param currentLocation
     * @param currentTime
     * @return
     */
    public int getPopularityRank(LocationOnRoad currentLocation, long currentTime){
        double[] location = currentLocation.toLatLon();
        int clusterIndex = getClusterIndex(new Point(location[1],location[0]));
        return getPopularityRank(clusterIndex,currentTime);
    }

    public int getPopularityRank(int clusterIndex, long currentTime){
        int timePeroid = calTimePeroid(currentTime);
        if(isWeekday(currentTime))
            return zonesPopularityRank.get(0).get(timePeroid).indexOf(clusterIndex)+1;
        return zonesPopularityRank.get(1).get(timePeroid).indexOf(clusterIndex)+1;
    }

    /**
     *
     * @param clusterIndex
     * @param currentTime
     * @return the central intersection of the cluster index
     */
    public Intersection getCenterIntersection(int clusterIndex, long currentTime){
       if(isPeakHours(currentTime))
           return peakCenterIntersections.get(clusterIndex);
       return nonPeakCenterIntersections.get(clusterIndex);
    }

    /**
     * Select a random intersection from the current zone
     * @param currentLocation
     * @return
     */
    public Intersection getRandomIntersection(LocationOnRoad currentLocation){
        int clusterIndex = getClusterIndex(currentLocation.road);
        return getRandomIntersection(clusterIndex);
    }

    public Intersection getRandomIntersection(int clusterIndex){
        List<Intersection> intersectionList = intersectionClusters.get(clusterIndex);
        Collections.shuffle(intersectionList);
        return intersectionList.get(0);
    }


    private void clustering(){
        //Read historical data and get the set of days
        CSVNewYorkParser parser = new CSVNewYorkParser(trainDataset, zone);
        ArrayList<Resource> resourcesParsed = parser.parse();

        try {
            for (Resource resource : resourcesParsed) {
                int dayth = calDayIndexOfYear(resource.getTime());
                daySet.add(dayth);
                if (isWeekday(resource.getTime()))
                    weekdaySet.add(dayth);
                else
                    weekendSet.add(dayth);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        // Select 7 days randomly and cluster the pick-up points
        Set<Integer> selectDays = new HashSet<>();
        if(daySet.size()>7){
            List<Integer> dayList = new ArrayList<>(daySet);
            Collections.shuffle(dayList);
            for(int i=0;i<7;i++)
                selectDays.add(dayList.get(i));
        }else{
            List<Integer> dayList = new ArrayList<>(daySet);
            for(int i=0;i<dayList.size();i++)
                selectDays.add(dayList.get(i));
        }
        ArrayList<Point> dataResources = new ArrayList<>();
        try {
            for (Resource resource : resourcesParsed) {
                Point point = new Point(resource.getPickupLon(),resource.getPickupLat());
                int dayIndex = calDayIndexOfYear(resource.getTime());
                if(selectDays.contains(dayIndex))
                    dataResources.add(point);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        KMeanClustering kc = new KMeanClustering(dataResources,clusterNum,map);
        List<List<Point>> pointClusters = kc.clustering();
        getList(pointClusters.size());
        // Get road segment classification results by cluster centers
        getRoadCluster(kc.clusteringCenterT);
    }

    /**
     * Initialize various lists
     */
    private void getList(int size){
        intersectionClusters = new ArrayList<>(size);
        roadClusters = new ArrayList<>(size);
        for(int i=0;i<size;i++){
            intersectionClusters.add(new ArrayList<>());
            roadClusters.add(new ArrayList<>());
        }
        nonPeakCenterIntersections = new ArrayList<>();
        peakCenterIntersections = new ArrayList<>();
    }

    /**
     * Divide intersections and roads into corresponding categories
     */
    private void getRoadCluster(List<Point> clusterCenters){
        for(Intersection intersection:map.intersections().values()){
            Point p = new Point(intersection.longitude,intersection.latitude);
            int index=0;
            double minDistance = clusterCenters.get(0).distance(p);
            for(int i=1;i<clusterCenters.size();i++){
                double distance = clusterCenters.get(i).distance(p);
                if(distance<minDistance){
                    index = i;
                    minDistance = distance;
                }
            }
            intersectionClusters.get(index).add(intersection);
        }

        for(int i=0;i<intersectionClusters.size();i++){
            for(int j=0;j<intersectionClusters.get(i).size();j++){
                for(Road road:intersectionClusters.get(i).get(j).getRoadsFrom()){
                    roadClusters.get(i).add(road);
                }
            }
        }
    }

    /**
     * Statistics on the spatio-temporal distribution of resources for planning search route
     */
    private void statisticResources(){
        // initialize statistic table
        initialMap();
        List<List<Point>> nonPeakPoints = new ArrayList<>();
        List<List<Point>> peakPoints = new ArrayList<>();
        for(int k=0;k<roadClusters.size();k++){
            nonPeakPoints.add(new ArrayList<>());
            peakPoints.add(new ArrayList<>());
        }

        try {
            Scanner sc = new Scanner(new File(trainDataset));   //scanner will scan the file specified by path
            sc.useDelimiter(",|\n");    //scanner will skip over "," and "\n" found in file
            sc.nextLine(); // skip the header

            //while there are tokens in the file the scanner will scan the input
            //each line in input file will contain 4 tokens for the scanner and will be in the format : latitude longitude time type
            //per line of input file we will create a new TimestampAgRe object
            // and save the 4 tokens of each line in the corresponding field of the TimestampAgRe object
            while (sc.hasNext()) {
                sc.next();// skip first VendorID
                long pickupTime = dateConversion(sc.next());
                long dropoffTime = dateConversion(sc.next());
                sc.next();// skip these fields
                sc.next();
                double pickupLon = Double.parseDouble(sc.next());
                double pickupLat = Double.parseDouble(sc.next());
                sc.next();// skip these fields
                sc.next();
                double dropoffLon = Double.parseDouble(sc.next());
                double dropoffLat = Double.parseDouble(sc.next());
                sc.nextLine(); //skip rest of fileds in this line
                // Only keep the resources such that both pickup location and dropoff location are within the bounding polygon.
                if (!(MapCreator.insidePolygon(pickupLon, pickupLat) && MapCreator.insidePolygon(dropoffLon, dropoffLat))) {
                    continue;
                }
                //统计上下车点
                Point pickupPoint = new Point(pickupLon, pickupLat);
                Point dropoffPoint = new Point(dropoffLon, dropoffLat);
                int pickTimeslot = calTimeSlot(pickupTime), dropTimeslot = calTimeSlot(dropoffTime), pickTimePeroid = calTimePeroid(pickupTime), dropTimePeroid = calTimePeroid(dropoffTime);
                int pickupClusterIndex = getClusterIndex(pickupPoint), dropoffClusterIndex = getClusterIndex(dropoffPoint);
                if (isPeakHours(pickupTime))
                    peakPoints.get(pickupClusterIndex).add(pickupPoint);
                else
                    nonPeakPoints.get(pickupClusterIndex).add(pickupPoint);
                if (isWeekday(pickupTime)) {
                    zoneTimeperiodPickup.get(0).get(pickTimePeroid).put(pickupClusterIndex, zoneTimeperiodPickup.get(0).get(pickTimePeroid).get(pickupClusterIndex) + 1);
                    zoneTimeperiodDropoff.get(0).get(dropTimePeroid).put(dropoffClusterIndex, zoneTimeperiodDropoff.get(0).get(dropTimePeroid).get(dropoffClusterIndex) + 1);
                    clusterAveragePickup.get(0).get(pickupClusterIndex).set(pickTimeslot, clusterAveragePickup.get(0).get(pickupClusterIndex).get(pickTimeslot) + 1);
                    clusterAverageDropoff.get(0).get(dropoffClusterIndex).set(dropTimeslot, clusterAverageDropoff.get(0).get(dropoffClusterIndex).get(dropTimeslot) + 1);
                } else {
                    zoneTimeperiodPickup.get(1).get(pickTimePeroid).put(pickupClusterIndex, zoneTimeperiodPickup.get(0).get(pickTimePeroid).get(pickupClusterIndex) + 1);
                    zoneTimeperiodDropoff.get(1).get(dropTimePeroid).put(dropoffClusterIndex, zoneTimeperiodDropoff.get(0).get(dropTimePeroid).get(dropoffClusterIndex) + 1);
                    clusterAveragePickup.get(1).get(pickupClusterIndex).set(pickTimeslot, clusterAveragePickup.get(1).get(pickupClusterIndex).get(pickTimeslot) + 1);
                    clusterAverageDropoff.get(1).get(dropoffClusterIndex).set(dropTimeslot, clusterAverageDropoff.get(1).get(dropoffClusterIndex).get(dropTimeslot) + 1);
                }
            }
            sc.close();
            //calculate geometric center
            for (int i = 0; i < roadClusters.size(); i++) {
                getCenterIntersection(nonPeakPoints.get(i), 0);
                getCenterIntersection(peakPoints.get(i), 1);
            }
            // calculate the mean value of each day in each time slot
            int timeSlotNum = 24*60/timeSlotLength;
            for(int i=0;i<clusterNum;i++){
                for(int j=0;j<timeSlotNum;j++){
                    clusterAveragePickup.get(0).get(i).set(j,clusterAveragePickup.get(0).get(i).get(j)/weekdaySet.size());
                    clusterAveragePickup.get(1).get(i).set(j,clusterAveragePickup.get(1).get(i).get(j)/weekendSet.size());
                    clusterAverageDropoff.get(0).get(i).set(j,clusterAverageDropoff.get(0).get(i).get(j)/weekdaySet.size());
                    clusterAverageDropoff.get(1).get(i).set(j,clusterAverageDropoff.get(1).get(i).get(j)/weekendSet.size());
                }
            }

            for(int i=0;i<24;i++){
                for(int j=0;j<roadClusters.size();j++){
                    zoneTimeperiodPickup.get(0).get(i).put(j,zoneTimeperiodPickup.get(0).get(i).get(j)/weekdaySet.size());
                    zoneTimeperiodPickup.get(1).get(i).put(j,zoneTimeperiodPickup.get(0).get(i).get(j)/weekendSet.size());
                    zoneTimeperiodDropoff.get(0).get(i).put(j,zoneTimeperiodDropoff.get(0).get(i).get(j)/weekdaySet.size());
                    zoneTimeperiodDropoff.get(1).get(i).put(j,zoneTimeperiodDropoff.get(0).get(i).get(j)/weekendSet.size());
                }
            }
            //get the popularity rank of each area
            getZonesRank();
        }catch (Exception ex){
            ex.printStackTrace();
        }
    }

    /**
     * Determine whether the current time is peak or not
     * @param currentTime
     * @return
     */
    public boolean isPeakHours(long currentTime){
        boolean isWeekday = isWeekday(currentTime);
        Instant instant = Instant.ofEpochSecond(currentTime);
        LocalDateTime ldt = LocalDateTime.ofInstant(instant,zone);
        int hour = ldt.getHour();
        if(isWeekday){
            if(hour>=17)
                return true;
            else
                return false;
        }
        if(hour>17&&hour<20)
            return true;
        return false;
    }

    /**
     * Get zone popularity rankings per hour
     */
    private void getZonesRank(){
        for(int i=0;i<2;i++) {
            Map<Integer, ArrayList<Integer>> zoneRanks = new HashMap<>();
            for (int j = 0; j < 24; j++) {
                Map<Integer, Double> zoneRank = new HashMap<>();
                for (int k = 0; k < roadClusters.size(); k++) {
                    double weight = zoneTimeperiodPickup.get(i).get(j).get(k) - 0.5 * zoneTimeperiodDropoff.get(i).get(j).get(k);
                    zoneRank.put(k, weight);
                }

                List<Map.Entry<Integer, Double>> list = new ArrayList<Map.Entry<Integer, Double>>(zoneRank.entrySet());
                Collections.sort(list, new Comparator<Map.Entry<Integer, Double>>() {
                    @Override
                    public int compare(Map.Entry<Integer, Double> o1, Map.Entry<Integer, Double> o2) {
                        return o2.getValue().compareTo(o1.getValue());
                    }
                });
                ArrayList<Integer> rank = new ArrayList<>();
                for (int k = 0; k < list.size(); k++) {
                    rank.add(list.get(k).getKey());
                }
                zoneRanks.put(j,rank);
            }
            zonesPopularityRank.put(i,zoneRanks);
        }
    }

    /**
     * Return the cluster index of the point
     * @param p
     * @return
     */
    public int getClusterIndex(Point p){
        //map matching
        LocationOnRoad location = mapMatch(p.lon,p.lat);
        Road road = location.road;
        for(int k=0;k<roadClusters.size();k++){
            if(contain(roadClusters.get(k),road))
                return k;
        }
        return 0;
    }

    public int getClusterIndex(Road road){
        int index = 0;
        for(int i=0;i<roadClusters.size();i++){
            if(contain(roadClusters.get(i),road)){
                index = i;
                break;
            }
        }
        return index;
    }

    /**
     * return the center point of point list
     * @param points
     * @param type peak hours or non-peak hours
     */
    private void getCenterIntersection(List<Point> points, int type){
        if(points.size()==0)
            return;
        //calculate the center of all points
        KMeanClustering kc = new KMeanClustering();
        Point centerPoint = kc.getCenterPoint(points);
        LocationOnRoad location = mapMatch(centerPoint.lon,centerPoint.lat);
        Road road = location.road;
        Intersection intersection = location.travelTimeFromStartIntersection>road.travelTime-location.travelTimeFromStartIntersection?road.to:road.from;
        if(type == 0)
            nonPeakCenterIntersections.add(intersection);
        else
            peakCenterIntersections.add(intersection);
    }

    /**
     * Initialize the value in the statistics table to 0
     */
    private void initialMap(){
        int timeSlotNum = 24*60/timeSlotLength;
        Map<Integer,ArrayList<Double>> weekdayDayPickup = new HashMap<>();
        Map<Integer,ArrayList<Double>> weekendDayPickup = new HashMap<>();
        Map<Integer,ArrayList<Double>> weekdayDayDropoff = new HashMap<>();
        Map<Integer,ArrayList<Double>> weekendDayDropoff = new HashMap<>();

        Map<Integer,Map<Integer,Double>> weekDayPickup2 = new HashMap<>();
        Map<Integer,Map<Integer,Double>> weekendPickup2 = new HashMap<>();
        Map<Integer,Map<Integer,Double>> weekDayDrop2 = new HashMap<>();
        Map<Integer,Map<Integer,Double>> weekendDrop2 = new HashMap<>();
        for(int i=0;i<24;i++){
            Map<Integer,Double> zoneWeekdayPickup = new HashMap<>();
            Map<Integer,Double> zoneWeekendPickup = new HashMap<>();
            Map<Integer,Double> zoneWeekdayDropoff = new HashMap<>();
            Map<Integer,Double> zoneWeekendDropoff = new HashMap<>();
            for(int j=0;j<roadClusters.size();j++){
                zoneWeekdayPickup.put(j,0.0);
                zoneWeekendPickup.put(j,0.0);
                zoneWeekdayDropoff.put(j,0.0);
                zoneWeekendDropoff.put(j,0.0);
            }
            weekDayPickup2.put(i,zoneWeekdayPickup);
            weekendPickup2.put(i,zoneWeekendPickup);
            weekDayDrop2.put(i,zoneWeekdayDropoff);
            weekendDrop2.put(i,zoneWeekendDropoff);
        }
        zoneTimeperiodPickup.put(0,weekDayPickup2);
        zoneTimeperiodPickup.put(1,weekendPickup2);
        zoneTimeperiodDropoff.put(0,weekDayDrop2);
        zoneTimeperiodDropoff.put(1,weekendDrop2);

        for(int i=0;i<roadClusters.size();i++) {
            ArrayList<Double> timeSlotTable1 = new ArrayList<>();
            ArrayList<Double> timeSlotTable2 = new ArrayList<>();
            ArrayList<Double> timeSlotTable3 = new ArrayList<>();
            ArrayList<Double> timeSlotTable4 = new ArrayList<>();
            for (int k = 0; k < timeSlotNum; k++) {
                timeSlotTable1.add(0.0);
                timeSlotTable2.add(0.0);
                timeSlotTable3.add(0.0);
                timeSlotTable4.add(0.0);
            }
            weekdayDayPickup.put(i,timeSlotTable1);
            weekendDayPickup.put(i,timeSlotTable2);
            weekdayDayDropoff.put(i,timeSlotTable3);
            weekendDayDropoff.put(i,timeSlotTable4);
        }
        clusterAveragePickup.put(0,weekdayDayPickup);
        clusterAveragePickup.put(1,weekendDayPickup);
        clusterAverageDropoff.put(0,weekdayDayDropoff);
        clusterAverageDropoff.put(1,weekendDayDropoff);
    }

    /**
     * Converts the date+time (timestamp) string into the Linux epoch.
     *
     * @param timestamp string containing formatted date and time data to be
     * converted
     * @return long value of the timestamp string
     */
    private Long dateConversion(String timestamp) {
        long l = 0L;
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        LocalDateTime ldt = LocalDateTime.parse(timestamp, dtf);
        ZonedDateTime zdt = ZonedDateTime.of(ldt, zone);

        l = zdt.toEpochSecond(); //Returns Linux epoch, i.e., the number of seconds since January 1, 1970, 00:00:00 GMT until time specified in zdt
        return l;
    }

    private boolean contain(List<Road> roadList,Road road){
        for(Road r:roadList){
            if(r.id == road.id){
                return true;
            }
        }
        return false;
    }

    /**
     * Match a point to the closest location on the map
     * @param longitude
     * @param latitude
     * @return
     */
    public LocationOnRoad mapMatch(double longitude, double latitude) {
        Link link = map.getNearestLink(longitude, latitude);
        double xy[] = map.projector().fromLatLon(latitude, longitude);
        double [] snapResult = snap(link.from.getX(), link.from.getY(), link.to.getX(), link.to.getY(), xy[0], xy[1]);
        double distanceFromStartVertex = this.distance(snapResult[0], snapResult[1], link.from.getX(), link.from.getY());
        long travelTimeFromStartVertex = Math.round(distanceFromStartVertex / link.length * link.travelTime);
        long travelTimeFromStartIntersection = link.beginTime + travelTimeFromStartVertex;
        return new LocationOnRoad(link.road, travelTimeFromStartIntersection);
    }

    /**
     * Find the closest point on a line segment with end points (x1, y1) and
     * (x2, y2) to a point (x ,y), a procedure called snap.
     *
     * @param x1 x-coordinate of an end point of the line segment
     * @param y1 y-coordinate of an end point of the line segment
     * @param x2 x-coordinate of another end point of the line segment
     * @param y2 y-coordinate of another end point of the line segment
     * @param x x-coordinate of the point to snap
     * @param y y-coordinate of the point to snap
     *
     * @return the closest point on the line segment.
     */
    public double[] snap(double x1, double y1, double x2, double y2, double x, double y) {
        double[] snapResult = new double[3];
        double dist;
        double length = (x1 - x2) * (x1 - x2) + (y1 - y2) * (y1 - y2);

        if (length == 0.0) {
            dist = this.distance(x1, y1, x, y);
            snapResult[0] = x1;
            snapResult[1] = y1;
            snapResult[2] = dist;
        } else {
            double t = ((x - x1) * (x2 - x1) + (y - y1) * (y2 - y1)) / length;
            if (t < 0.0) {
                dist = distance(x1, y1, x, y);
                snapResult[0] = x1;
                snapResult[1] = y1;
                snapResult[2] = dist;
            } else if (t > 1.0) {
                dist = distance(x2, y2, x, y);
                snapResult[0] = x2;
                snapResult[1] = y2;
                snapResult[2] = dist;
            } else {
                double proj_x = x1 + t * (x2 - x1);
                double proj_y = y1 + t * (y2 - y1);
                dist = distance(proj_x, proj_y, x, y);
                snapResult[0] = proj_x;
                snapResult[1] = proj_y;
                snapResult[2] = dist;
            }
        }
        return snapResult;
    }

    /**
     * Compute the Euclidean distance between point (x1, y1) and point (x2, y2).
     */
    public double distance(double x1, double y1, double x2, double y2) {
        return Math.sqrt((x1 - x2) * (x1 - x2) + (y1 - y2) * (y1 - y2));
    }

    /**
     Calculate the time slot index corresponding to the timestamp, return the index, start from 0
     */
    public int calTimeSlot(long timestamp){
        Instant instant = Instant.ofEpochSecond(timestamp);
        LocalDateTime ldt = LocalDateTime.ofInstant(instant,zone);
        int hour = ldt.getHour();
        int minute = ldt.getMinute();
        return (hour*60+minute)/timeSlotLength;
    }

    /**
     * Calculate the time period corresponding to the timestamp, one time period per hour
     */
    public int calTimePeroid(long timestamp){
        Instant instant = Instant.ofEpochSecond(timestamp);
        LocalDateTime ldt = LocalDateTime.ofInstant(instant,zone);
        int hour = ldt.getHour();
        return hour;
    }


    /**
     Calculate the index of day in the year corresponding to the timestamp
     return the index, from 1-366
     */
    public int calDayIndexOfYear(long timestamp){
        Instant instant = Instant.ofEpochSecond(timestamp);
        LocalDateTime ldt = LocalDateTime.ofInstant(instant,zone);
        int day = ldt.getDayOfYear();
        return day;
    }

    /**
     Calculate the day of the week corresponding to the timestamp
     and return the subscript from Monday to Sunday
     */
    public int calDayIndexOfWeek(long timestamp){
        Instant instant = Instant.ofEpochSecond(timestamp);
        LocalDateTime ldt = LocalDateTime.ofInstant(instant,zone);
        int day = ldt.getDayOfWeek().getValue();
        return day;
    }


    /**
     * Calculate whether the current timestamp is weekday or weekend
     * @param timestamp
     * @return
     */
    public boolean isWeekday(long timestamp){
        int dayth = calDayIndexOfWeek(timestamp);
        if(dayth==6||dayth==7){
            return false;
        }
        else{
            return true;
        }
    }

    public class Point{
        double lon,lat;
        public Point(double lon,double lat){
            this.lon = lon;
            this.lat = lat;
        }

        @Override
        public boolean equals(Object obj) {
            Point point = (Point) obj;
            if(String.valueOf(this.lon)==String.valueOf(point.lon)&&String.valueOf(this.lat)==String.valueOf(point.lat)){
                return true;
            }
            return false;
        }

        public double distance(Point p){
            double px = this.lon - p.lon;
            double py = this.lat - p.lat;
            return Math.sqrt(px * px + py * py);
        }
    }

    public class KMeanClustering {
        public CityMap map;
        private List<Point> dataArray;
        private int K;
        private int maxClusterTimes = 500; //The maximum number of iterations
        private List<List<Point>> clusterList; //The result of clustering
        private List<Point> clusteringCenterT; //Cluster centers

        public KMeanClustering(){
        }

        public KMeanClustering(List<Point> data, int K, CityMap map){
            dataArray = data;
            this.map = map;
            this.K = K;
        }

        public int getK(){
            return K;
        }

        public void setK(int K){
            if(K<1){
                throw new IllegalArgumentException("K must greater than 0");
            }
            this.K = K;
        }

        public int getMaxClusterTimes(){
            return maxClusterTimes;
        }

        public void setMaxClusterTimes(int maxClusterTimes) {
            if(maxClusterTimes<10){
                throw new IllegalArgumentException("maxClusterTimes must greater than 10");
            }
            this.maxClusterTimes = maxClusterTimes;
        }

        public List<Point> getClusteringCenterT() {
            return clusteringCenterT;
        }

        public List<List<Point>> clustering() {
            if(dataArray == null){
                return null;
            }
            int size = K > dataArray.size()?dataArray.size():K;
            List<Point> centerT = new ArrayList<>(size);
            Collections.shuffle(dataArray);
            for(int i=0;i<size;i++){
                centerT.add(dataArray.get(i));
            }
            clustering(centerT,0);
            return clusterList;
        }

        private void clustering(List<Point> preCenter, int times){
            if(preCenter==null||preCenter.size()<2){
                return;
            }
            Collections.shuffle(preCenter);
            List<List<Point>> clusterList = getListPoint(preCenter.size());
            for(Point point:dataArray){
                //Find the most similar centroid
                int max=0;
                double maxScore = similarScore(point,preCenter.get(0));
                for(int i=1;i<preCenter.size();i++){
                    if(maxScore<similarScore(point,preCenter.get(i))){
                        max = i;
                        maxScore = similarScore(point,preCenter.get(i));
                    }
                }
                clusterList.get(max).add(point);
            }
            //Calculate the centroid of each cluster in this clustering result
            List<Point> nowCenter = new ArrayList<>();
            for(List<Point> list:clusterList){
                nowCenter.add(getCenterPoint(list));
            }
            //Whether the maximum number of iterations is reached
            if(times >= maxClusterTimes||preCenter.size()<K){
                this.clusterList = clusterList;
                this.clusteringCenterT = nowCenter;
                return;
            }
            this.clusteringCenterT = nowCenter;
            if(isCenterChange(preCenter,nowCenter)){
                clear(clusterList);
                clustering(nowCenter,times+1);
            }else{
                this.clusterList = clusterList;
            }
        }

        private List<List<Point>> getListPoint(int size){
            List<List<Point>> list = new ArrayList<>(size);
            for(int i=0;i<size;i++){
                list.add(new ArrayList<>());
            }
            return list;
        }

        private void clear(List<List<Point>> lists){
            for(List<Point> list:lists){
                list.clear();
            }
            lists.clear();
        }

        private boolean isCenterChange(List<Point> preCenter,List<Point> nowCenter){
            if(preCenter==null||nowCenter==null){
                return false;
            }
            for(Point p1 :preCenter){
                boolean bol = true;
                for(Point p2:nowCenter){
                    if(p1.equals(p2)){
                        bol = false;
                        break;
                    }
                }
                if(bol){
                    return bol;
                }
            }
            return false;
        }

        public double similarScore(Point p1,Point p2){
            double distance = Math.sqrt((p1.lon - p2.lon) * (p1.lon - p2.lon) + (p1.lat - p2.lat) * (p1.lat - p2.lat));
            return -distance;
        }

        public Point getCenterPoint(List<Point> list){
            //Select the geometric center of all points as the cluster center
            double lon = 0.0,lat = 0.0;
            try {
                for (Point point : list) {
                    lon += point.lon;
                    lat += point.lat;
                }
                lon = lon / list.size();
                lat = lat / list.size();
            } catch(Exception e) {
                e.printStackTrace();
            }
            return new Point(lon,lat);
        }
    }


}
