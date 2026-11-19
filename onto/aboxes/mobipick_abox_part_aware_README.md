# MobiPick part-aware ABoxes

## Files

- `mobipick_baseline_part_aware.ttl`: three objects, no box.
- `mobipick_box_capacity_2_part_aware.ttl`: same objects plus box, capacity 2.
- `mobipick_box_capacity_1_part_aware.ttl`: same objects plus box, capacity 1.

## Embodiment matching the Java method

`agent dul:hasConstituent robot`; `robot dul:hasPart mobipick_base, mobipick_arm`; `mobipick_arm dul:hasPart mobipick_camera`. All three are `mc:RobotPart`. The base alone bears navigation capability, pose and orientation; the arm bears grasping capability, and the camera bears object detection. Labels follow the Java source. Base home is represented by the symbolic `region_nav0`, annotated with Java's coordinates (20.50,15.20,0.0). The Java orientation (0,0,1,0) is documented but not serialized as a precise numeric quaternion because the helper `assertDiscreteOrientation` is unavailable.

## Connectivity and opportunities

Each `navN` bears `mc:ReachabilityDisposition` `navN_reachability`; `mc:connects` points to each other `navM` (12 directed navigation edges). The same disposition connects to objects available for grasping from nav1/nav2, and to the access disposition of `place1`/`place2`/`place3`. The last pattern is provisional until the PlaceGraspable production rule is finalized. Navigation locations and placing areas share `mc:Pose` quality type. Each location additionally bears a `mc:Containment` quality with a `mc:BinaryCapacityRegion` to reflect TBox restrictions; occupancy values are intentionally not invented.
