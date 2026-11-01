package hu.helmetsoft.homedesigner.model;

import hu.helmetsoft.homedesigner.util.I18n;

/** Global landscaping footprint; elevation is absolute, independent of the edited storey. */
public class SiteFill {
    public String name=I18n.text("Földfeltöltés");
    public SlabOpening outline=new SlabOpening();
    public double elevation=0.5, slopeWidth=2;
    public boolean retaining;
    public Finish finish=new Finish("grass",null);

    public SiteFill copy() {
        SiteFill f=new SiteFill();
        f.name=name; f.outline=outline.copy(); f.elevation=elevation;
        f.slopeWidth=slopeWidth; f.retaining=retaining; f.finish=finish.copy();
        return f;
    }
}
