package cz.muni.fi.umlspnp.views.deploymentdiagram;

import cz.muni.fi.umlspnp.views.common.NamedRectangle;
import javafx.scene.paint.Color;

/**
 * View rendering the artifact (component) in the deployment diagram.
 *
 */
public class ArtifactView extends NamedRectangle {

    public ArtifactView(double x, double y, double width, double height, int modelObjectID) {
        super(x, y, width, height, "New artifact", modelObjectID);
        
        this.setFill(Color.WHITE);
    }
}
