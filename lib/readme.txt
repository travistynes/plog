Local dependencies (jars) can go here. Gradle can be configured to add them to the compile time classpath:

dependencies {
    compile fileTree(dir: "lib", include: "**/*.jar")
}