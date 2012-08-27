package uk.ac.rdg.resc.ncwms.wms;

public interface FalseColorLayer extends ScalarLayer {

    public ScalarLayer getRedLayer();

    public ScalarLayer getGreenLayer();

    public ScalarLayer getBlueLayer();

}
