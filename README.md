# RecipeViewer

Browse vanilla and Nexo recipes through configurable categories and recipe views.
Requires Java 25 and Paper 26.3. Existing category files and permissions are retained.

## Build and test

```sh
./gradlew clean build
```

Install the shaded `build/libs/recipe-viewer-1.1.1-SNAPSHOT-all.jar`.

The event regressions exercise InventoryFramework and independent protection for
category and recipe-detail inventories, including navigation callbacks, outside
clicks, shifts, hotbar/offhand swaps, drops, drag, creative clicks and pickup.
Category menus keep their original read-only player-inventory behavior while open;
ordinary inventories and legitimate entity pickup are unaffected.

To additionally load an exact Paper runtime without starting a server, put the
server JAR and its dependency JARs in a newline-separated classpath file and run:

```sh
./gradlew clean build -PpaperRuntimeClasspath=/absolute/path/paper-classpath.txt
```

This also bootstraps Minecraft registries and exercises real CraftInventoryView,
CraftInventoryCustom and container-slot implementations. The player/menu boundaries
remain test doubles. These checks do not replace authenticated client testing.
