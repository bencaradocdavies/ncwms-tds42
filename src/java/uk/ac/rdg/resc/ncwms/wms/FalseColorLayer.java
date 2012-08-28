package uk.ac.rdg.resc.ncwms.wms;

/**
 * A scalar layer that consists of three colour components that can be combined
 * to form a false colour layer.
 * 
 * @author Ben Caradoc-Davies, CSIRO Earth Science and Resource Engineering
 */
public interface FalseColorLayer extends ScalarLayer {

    public ScalarLayer getRedComponent();

    public ScalarLayer getGreenComponent();

    public ScalarLayer getBlueComponent();

}
