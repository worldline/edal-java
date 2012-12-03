package uk.ac.rdg.resc.godiva.client;

import uk.ac.rdg.resc.godiva.client.state.ElevationSelectorIF;
import uk.ac.rdg.resc.godiva.client.state.PaletteSelectorIF;
import uk.ac.rdg.resc.godiva.client.state.TimeSelectorIF;
import uk.ac.rdg.resc.godiva.client.state.UnitsInfoIF;
import uk.ac.rdg.resc.godiva.client.widgets.AnimationButton;
import uk.ac.rdg.resc.godiva.client.widgets.MapArea;

import com.google.gwt.user.client.ui.AbsolutePanel;
import com.google.gwt.user.client.ui.Anchor;
import com.google.gwt.user.client.ui.HasVerticalAlignment;
import com.google.gwt.user.client.ui.HorizontalPanel;
import com.google.gwt.user.client.ui.Image;
import com.google.gwt.user.client.ui.IsWidget;
import com.google.gwt.user.client.ui.PushButton;
import com.google.gwt.user.client.ui.ScrollPanel;
import com.google.gwt.user.client.ui.VerticalPanel;
import com.google.gwt.user.client.ui.Widget;


/**
 * A class containing static methods for returning different layouts.  This means that
 * multiple viewports can be configured here from the same codebase with very little change 
 * 
 * @author guy
 */
public class LayoutManager {
    
    public static Widget getGodiva3Layout(IsWidget layerSelector,
            UnitsInfoIF unitsInfo,
            TimeSelectorIF timeSelector,
            ElevationSelectorIF elevationSelector,
            PaletteSelectorIF paletteSelector,
            Anchor kmzLink,
            Anchor permalink,
            Anchor email,
            Anchor screenshot,
            Image rescLogo,
            MapArea mapArea,
            Image loadingImage,
            AnimationButton anim,
            PushButton infoButton){
        
        kmzLink.setStylePrimaryName("linkStyle");
        permalink.setStylePrimaryName("linkStyle");
        email.setStylePrimaryName("linkStyle");
        screenshot.setStylePrimaryName("linkStyle");
        
        VerticalPanel selectors = new VerticalPanel();
        selectors.add(layerSelector);
        selectors.add(unitsInfo);
        selectors.add(timeSelector);
        selectors.add(elevationSelector);
        
        /*
         * The image height is hardcoded here, because when running with IE8, rescLogo.getHeight()
         * returns 0 instead of the actual height...
         */
        int logoHeight = rescLogo.getHeight();
        if(logoHeight == 0)
            logoHeight = 52;
        selectors.setHeight(logoHeight+"px");
        
        HorizontalPanel bottomPanel = new HorizontalPanel();
        bottomPanel.setVerticalAlignment(HasVerticalAlignment.ALIGN_MIDDLE);
        anim.setWidth("16px");
        bottomPanel.add(anim);
        bottomPanel.setWidth("100%");
        bottomPanel.add(kmzLink);
        bottomPanel.add(permalink);
        bottomPanel.add(email);
        bottomPanel.add(screenshot);
        infoButton.setWidth("16px");
        bottomPanel.add(infoButton);
        
        HorizontalPanel topPanel = new HorizontalPanel();
        
        topPanel.add(rescLogo);
        topPanel.add(selectors);
        topPanel.setCellVerticalAlignment(rescLogo, HasVerticalAlignment.ALIGN_MIDDLE);
        
        HorizontalPanel mapPalettePanel = new HorizontalPanel();
        mapPalettePanel.add(mapArea);
        mapPalettePanel.add(paletteSelector);
        
        /*
         * We introduce an AbsolutePanel here. Generally I like to avoid them,
         * but it allows us to overlay a loading image. It's introduced at the
         * last possible moment to avoid any ugliness related to absolute
         * positioning
         */
        AbsolutePanel mapPaletteLoaderPanel = new AbsolutePanel();
        mapPaletteLoaderPanel.add(mapPalettePanel);
        int loaderHeight = loadingImage.getHeight();
        int loaderWidth = loadingImage.getWidth();
        if(loaderHeight == 0)
            loaderHeight = 19;
        if(loaderWidth == 0)
            loaderWidth = 220;
        mapPaletteLoaderPanel.add(loadingImage,
                (int) (mapArea.getMap().getSize().getWidth() - loaderWidth) / 2, (int) (mapArea
                        .getMap().getSize().getHeight() - loaderHeight) / 2);
        
        VerticalPanel vPanel = new VerticalPanel();
        vPanel.add(topPanel);
        
        vPanel.setCellHeight(topPanel, logoHeight+"px");
        vPanel.setCellWidth(topPanel, ((int) mapArea.getMap().getSize().getWidth()+100)+"px");
        vPanel.add(mapPaletteLoaderPanel);
        
        vPanel.add(bottomPanel);
        vPanel.setCellHeight(bottomPanel, "100%");
        vPanel.setCellVerticalAlignment(bottomPanel, HasVerticalAlignment.ALIGN_MIDDLE);
        
        ScrollPanel scrollPanel = new ScrollPanel(vPanel);
        
        return scrollPanel;
    }
}