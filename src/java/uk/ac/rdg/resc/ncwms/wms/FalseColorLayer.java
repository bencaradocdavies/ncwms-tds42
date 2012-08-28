package uk.ac.rdg.resc.ncwms.wms;

/**
 * A scalar layer that consists of three colour components that can be combined
 * to form a false colour layer.
 * 
 * @author Ben Caradoc-Davies, CSIRO Earth Science and Resource Engineering
 */
public interface FalseColorLayer extends ScalarLayer {

    /**
     * Get the layer whose values are interpreted as the red component of the
     * false colour layer.
     */
    public ScalarLayer getRedComponent();

    /**
     * Get the layer whose values are interpreted as the green component of the
     * false colour layer.
     */
    public ScalarLayer getGreenComponent();

    /**
     * Get the layer whose values are interpreted as the blue component of the
     * false colour layer.
     */
    public ScalarLayer getBlueComponent();

}
