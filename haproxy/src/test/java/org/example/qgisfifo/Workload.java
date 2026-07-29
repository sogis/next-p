package org.example.qgisfifo;

/** Request shapes used by direct-baseline and HAProxy integration tests. */
public enum Workload {
    SHORT(
        "SERVICE=WMS&VERSION=1.3.0&REQUEST=GetCapabilities",
        "text/xml"
    ),
    HEAVY(
        "SERVICE=WMS&VERSION=1.3.0&REQUEST=GetMap&LAYERS=fifo_grid&STYLES="
            + "&CRS=EPSG:4326&BBOX=-180,-90,180,90&WIDTH=3072&HEIGHT=3072"
            + "&FORMAT=image/png&TRANSPARENT=FALSE",
        "image/png"
    );

    private final String defaultQuery;
    private final String expectedContentType;

    Workload(String defaultQuery, String expectedContentType) {
        this.defaultQuery = defaultQuery;
        this.expectedContentType = expectedContentType;
    }

    public String query(FifoTestConfig config) {
        return this == SHORT ? config.shortWorkloadQuery() : config.heavyWorkloadQuery();
    }

    public String expectedContentType() {
        return expectedContentType;
    }

    public String defaultQuery() {
        return defaultQuery;
    }
}
