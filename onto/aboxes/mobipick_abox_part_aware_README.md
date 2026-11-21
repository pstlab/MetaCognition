# MobiPick part-aware ABoxes

## Files

- `mobipick_baseline_part_aware.ttl`: three objects, no box.
- `mobipick_box_capacity_2_part_aware.ttl`: same objects plus box, capacity 2.
- `mobipick_box_capacity_1_part_aware.ttl`: same objects plus box, capacity 1.

## Embodiment matching the Java method

`agent dul:hasConstituent robot`; `robot dul:hasPart mobipick_base, mobipick_arm`; `mobipick_arm dul:hasPart mobipick_camera, mobipick_gripper`. These physical components are `mc:RobotPart`. The base plays a distinct `mc:NavigationReferencePart` role and the gripper plays `mc:GraspingReferencePart`, both through `dul:hasRole`; roles are not physical parts. The base bears navigation capability, pose and orientation; the gripper bears grasping capability and the pose used by PickGraspable alignments; the camera bears object detection. Labels follow the Java source. Base and initial gripper pose use the symbolic `region_nav0`, corresponding to Java's initial coordinates (20.50,15.20,0.0). The Java orientation (0,0,1,0) is documented but not serialized as a precise numeric quaternion because the helper `assertDiscreteOrientation` is unavailable.

## Connectivity and opportunities

Each `navN` bears `mc:ReachabilityDisposition` `navN_reachability`; `mc:connects` points to each other `navM` (12 directed navigation edges). The same navigation disposition connects directly to objects available for grasping from nav1/nav2. These `NavigationLocation -> ReachabilityDisposition -> InteractingObject` paths are the only location paths used by PickGraspable. A navigation disposition also connects to its corresponding `placeN` manipulation destination; these `NavigationLocation -> ReachabilityDisposition -> ManipulationLocation` paths ground PlaceGraspable. The destination Pose Region is used as the placed object's resulting Pose Region. Navigation locations and placing areas share `mc:Pose` quality type. Each location additionally bears a `mc:Containment` quality with a `mc:BinaryCapacityRegion` to reflect TBox restrictions; occupancy values are intentionally not invented.
