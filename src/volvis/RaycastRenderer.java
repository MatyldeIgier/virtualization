/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package volvis;

import com.jogamp.opengl.util.texture.Texture;
import com.jogamp.opengl.util.texture.awt.AWTTextureIO;
import gui.RaycastRendererPanel;
import gui.TransferFunction2DEditor;
import gui.TransferFunctionEditor;
import java.awt.image.BufferedImage;
import java.util.concurrent.TimeUnit;
import javax.media.opengl.GL;
import javax.media.opengl.GL2;
import util.TFChangeListener;
import util.VectorMath;
import volume.GradientVolume;
import volume.Volume;
import volume.VoxelGradient;

/**
 *
 * @author michel
 */
public class RaycastRenderer extends Renderer implements TFChangeListener {

    private Volume volume = null;
    private GradientVolume gradients = null;
    RaycastRendererPanel panel;
    TransferFunction tFunc;
    TransferFunctionEditor tfEditor;
    TransferFunction2DEditor tfEditor2D;
    
    public RaycastRenderer() {
        panel = new RaycastRendererPanel(this);
        panel.setSpeedLabel("0",false);
    }

    public void setVolume(Volume vol) {
        System.out.println("Assigning volume");
        volume = vol;

        System.out.println("Computing gradients");
        gradients = new GradientVolume(vol);

        // set up image for storing the resulting rendering
        // the image width and height are equal to the length of the volume diagonal
        int imageSize = (int) Math.floor(Math.sqrt(vol.getDimX() * vol.getDimX() + vol.getDimY() * vol.getDimY()
                + vol.getDimZ() * vol.getDimZ()));
        if (imageSize % 2 != 0) {
            imageSize = imageSize + 1;
        }
        image = new BufferedImage(imageSize, imageSize, BufferedImage.TYPE_INT_ARGB);
        // create a standard TF where lowest intensity maps to black, the highest to white, and opacity increases
        // linearly from 0.0 to 1.0 over the intensity range
        tFunc = new TransferFunction(volume.getMinimum(), volume.getMaximum());
        
        // uncomment this to initialize the TF with good starting values for the orange dataset 
        //tFunc.setTestFunc();
        
        
        tFunc.addTFChangeListener(this);
        tfEditor = new TransferFunctionEditor(tFunc, volume.getHistogram());
        
        tfEditor2D = new TransferFunction2DEditor(volume, gradients);
        tfEditor2D.addTFChangeListener(this);

        System.out.println("Finished initialization of RaycastRenderer");
    }

    public RaycastRendererPanel getPanel() {
        return panel;
    }

    public TransferFunction2DEditor getTF2DPanel() {
        return tfEditor2D;
    }
    
    public TransferFunctionEditor getTFPanel() {
        return tfEditor;
    }
    
    //unused
    short getVoxel(double[] coord) {

        if (coord[0] < 0 || coord[0] > volume.getDimX() || coord[1] < 0 || coord[1] > volume.getDimY()
                || coord[2] < 0 || coord[2] > volume.getDimZ()) {
            return 0;
        }

        int x = (int) Math.floor(coord[0]);
        int y = (int) Math.floor(coord[1]);
        int z = (int) Math.floor(coord[2]);

        return volume.getVoxel(x, y, z);
    }

    void slicer(double[] viewMatrix) {

        // clear image
        for (int j = 0; j < image.getHeight(); j++) {
            for (int i = 0; i < image.getWidth(); i++) {
                image.setRGB(i, j, 0);
            }
        }

        // vector uVec and vVec define a plane through the origin, 
        // perpendicular to the view vector viewVec
        double[] viewVec = new double[3];
        double[] uVec = new double[3];
        double[] vVec = new double[3];
        VectorMath.setVector(viewVec, viewMatrix[2], viewMatrix[6], viewMatrix[10]);
        VectorMath.setVector(uVec, viewMatrix[0], viewMatrix[4], viewMatrix[8]);
        VectorMath.setVector(vVec, viewMatrix[1], viewMatrix[5], viewMatrix[9]);

        // image is square
        int imageCenter = image.getWidth() / 2;

        double[] pixelCoord = new double[3];
        double[] volumeCenter = new double[3];
        VectorMath.setVector(volumeCenter, volume.getDimX() / 2, volume.getDimY() / 2, volume.getDimZ() / 2);

        // sample on a plane through the origin of the volume data
        double max = volume.getMaximum();
        TFColor voxelColor = new TFColor();

        
        for (int j = 0; j < image.getHeight(); j++) {
            for (int i = 0; i < image.getWidth(); i++) {
                pixelCoord[0] = uVec[0] * (i - imageCenter) + vVec[0] * (j - imageCenter)
                        + volumeCenter[0];
                pixelCoord[1] = uVec[1] * (i - imageCenter) + vVec[1] * (j - imageCenter)
                        + volumeCenter[1];
                pixelCoord[2] = uVec[2] * (i - imageCenter) + vVec[2] * (j - imageCenter)
                        + volumeCenter[2];

                int val = getVoxelTLI(pixelCoord[0],pixelCoord[1],pixelCoord[2]);
                
                // Map the intensity to a grey value by linear scaling
                voxelColor.r = val/max;
                voxelColor.g = voxelColor.r;
                voxelColor.b = voxelColor.r;
                voxelColor.a = val > 0 ? 1.0 : 0.0;  // this makes intensity 0 completely transparent and the rest opaque
                // Alternatively, apply the transfer function to obtain a color
                // voxelColor = tFunc.getColor(val);
                
                
                // BufferedImage expects a pixel color packed as ARGB in an int
                int c_alpha = voxelColor.a <= 1.0 ? (int) Math.floor(voxelColor.a * 255) : 255;
                int c_red = voxelColor.r <= 1.0 ? (int) Math.floor(voxelColor.r * 255) : 255;
                int c_green = voxelColor.g <= 1.0 ? (int) Math.floor(voxelColor.g * 255) : 255;
                int c_blue = voxelColor.b <= 1.0 ? (int) Math.floor(voxelColor.b * 255) : 255;
                int pixelColor = (c_alpha << 24) | (c_red << 16) | (c_green << 8) | c_blue;
                image.setRGB(i, j, pixelColor);
            }
        }

    }
    
    void mip(double[] viewMatrix, int resolution) {
        
        // clear image
        for (int j = 0; j < image.getHeight(); j++) {
            for (int i = 0; i < image.getWidth(); i++) {
                image.setRGB(i, j, 0);
            }
        }

        // vector uVec and vVec define a plane through the origin, 
        // perpendicular to the view vector viewVec
        double[] viewVec = new double[3];//faire moyenne sur ça
        double[] uVec = new double[3];
        double[] vVec = new double[3];
        VectorMath.setVector(viewVec, viewMatrix[2], viewMatrix[6], viewMatrix[10]);
        VectorMath.setVector(uVec, viewMatrix[0], viewMatrix[4], viewMatrix[8]);
        VectorMath.setVector(vVec, viewMatrix[1], viewMatrix[5], viewMatrix[9]);

        // image is square
        int imageCenter = image.getWidth() / 2;

        double[] pixelCoord = new double[3];
        double[] volumeCenter = new double[3];
        VectorMath.setVector(volumeCenter, volume.getDimX() / 2, volume.getDimY() / 2, volume.getDimZ() / 2);

        // sample on a plane through the origin of the volume data
        double max = volume.getMaximum();
        TFColor voxelColor = new TFColor();

        
        //Calcule automatiquement la range
        int range = min4((int) (volume.getDimX() / viewVec[0]),
                        (int) (volume.getDimY() / viewVec[1]),
                        (int) (volume.getDimZ() / viewVec[2]),
                        500);
        
        for (int j = 0; j < image.getHeight(); j++) {
            for (int i = 0; i < image.getWidth(); i++) {
                    pixelCoord[0] = uVec[0] * (i - imageCenter) + vVec[0] * (j - imageCenter)
                            + volumeCenter[0];
                    pixelCoord[1] = uVec[1] * (i - imageCenter) + vVec[1] * (j - imageCenter)
                            + volumeCenter[1];
                    pixelCoord[2] = uVec[2] * (i - imageCenter) + vVec[2] * (j - imageCenter)
                            + volumeCenter[2];
                
                //Le probleme etait qu'on recalculait la position du pixel a chaque fois, pas nécessaire.
                int val = 0;
                
                
                //Back to front
                for(int k = -range; k <= range; k=k+resolution){
                    
                    short v = getVoxelTLI(pixelCoord[0]+k*viewVec[0],
                                          pixelCoord[1]+k*viewVec[1],
                                          pixelCoord[2]+k*viewVec[2]);
                    
                    if(v > val) val=v;
                }
                
                // Map the intensity to a grey value by linear scaling
                voxelColor.r = val/max;
                voxelColor.g = voxelColor.r;
                voxelColor.b = voxelColor.r;
                voxelColor.a = val > 0 ? 1.0 : 0.0;  // this makes intensity 0 completely transparent and the rest opaque
                // Alternatively, apply the transfer function to obtain a color
                //voxelColor = tFunc.getColor(val);
                
                
                // BufferedImage expects a pixel color packed as ARGB in an int
                int c_alpha = voxelColor.a <= 1.0 ? (int) Math.floor(voxelColor.a * 255) : 255;
                int c_red = voxelColor.r <= 1.0 ? (int) Math.floor(voxelColor.r * 255) : 255;
                int c_green = voxelColor.g <= 1.0 ? (int) Math.floor(voxelColor.g * 255) : 255;
                int c_blue = voxelColor.b <= 1.0 ? (int) Math.floor(voxelColor.b * 255) : 255;
                int pixelColor = (c_alpha << 24) | (c_red << 16) | (c_green << 8) | c_blue;
                image.setRGB(i, j, pixelColor);
            }
        } 
        
        //System.out.println("View vect : ("+ viewVec[0]+","+viewVec[1]+","+viewVec[2]+")");
        //System.out.println("U vect : ("+ uVec[0]+","+uVec[1]+","+uVec[2]+")");
        //System.out.println("V vect : ("+ vVec[0]+","+vVec[1]+","+vVec[2]+")");

    }
    
    //calc the voxel using TLI
    short getVoxelTLI(double x, double y, double z) {
        //if the coordinate are out of bond, return 0
        if ( (x < 0) || (x > volume.getDimX() - 1) || (y < 0) || (y > volume.getDimY() - 1)
                || (z < 0) || (z > volume.getDimZ() - 1) ) {
            return 0;
        }
        //else calc voxel with TLI
        int x0 = (int) Math.floor(x);
        int y0 = (int) Math.floor(y);
        int z0 = (int) Math.floor(z);
        
        int x1 = (int) Math.ceil(x);
        int y1 = (int) Math.ceil(y);
        int z1 = (int) Math.ceil(z);

        double xd =(x - x0);
        double yd =(y - y0);
        double zd =(z - z0);

        double c00 = volume.getVoxel(x0, y0, z0)*(1 - xd) + volume.getVoxel(x1, y0, z0)*xd;
        double c01 = volume.getVoxel(x0, y0, z1)*(1 - xd) + volume.getVoxel(x1, y0, z1)*xd;
        double c10 = volume.getVoxel(x0, y1, z0)*(1 - xd) + volume.getVoxel(x1, y1, z0)*xd;
        double c11 = volume.getVoxel(x0, y1, z1)*(1 - xd) + volume.getVoxel(x1, y1, z1)*xd;

        double c0 = c00*(1 - yd) + c10*yd;
        double c1 = c01*(1 - yd) + c11*yd;
        
        double c = c0*(1 - zd) + c1*zd;

        return (short) c;
    }
    
    int min4(int i, int j, int k, int defaultValue) {
        int returnValue;
        if (Math.abs(i) <= Math.abs(j) && Math.abs(i) <= Math.abs(k)) {
            returnValue = Math.abs(i) > 0 ? Math.abs(i) : defaultValue ;
        } else if (Math.abs(j) <= Math.abs(k)) {
            returnValue = Math.abs(j) > 0 ? Math.abs(j) : defaultValue ;
        } else {
            returnValue = Math.abs(k) > 0 ? Math.abs(k) : defaultValue ;
        }
        //System.out.println("min4 "+ returnValue);
        return returnValue;
    }
    
    void compositing(double[] viewMatrix, int resolution) {

        // clear image
        for (int j = 0; j < image.getHeight(); j++) {
            for (int i = 0; i < image.getWidth(); i++) {
                image.setRGB(i, j, 0);
            }
        }

        // vector uVec and vVec define a plane through the origin, 
        // perpendicular to the view vector viewVec
        double[] viewVec = new double[3];//faire moyenne sur ça
        double[] uVec = new double[3];
        double[] vVec = new double[3];
        VectorMath.setVector(viewVec, viewMatrix[2], viewMatrix[6], viewMatrix[10]);
        VectorMath.setVector(uVec, viewMatrix[0], viewMatrix[4], viewMatrix[8]);
        VectorMath.setVector(vVec, viewMatrix[1], viewMatrix[5], viewMatrix[9]);

        // image is square
        int imageCenter = image.getWidth() / 2;

        double[] pixelCoord = new double[3];
        double[] volumeCenter = new double[3];
        VectorMath.setVector(volumeCenter, volume.getDimX() / 2, volume.getDimY() / 2, volume.getDimZ() / 2);

        // sample on a plane through the origin of the volume data
        double max = volume.getMaximum();
        TFColor voxelColor = new TFColor();

        //Calcule automatiquement la range
        int range = min4((int) (volume.getDimX() / viewVec[0]),
                        (int) (volume.getDimY() / viewVec[1]),
                        (int) (volume.getDimZ() / viewVec[2]),
                        500);
        
        for (int j = 0; j < image.getHeight(); j++) {
            for (int i = 0; i < image.getWidth(); i++) {
                
                    pixelCoord[0] = uVec[0] * (i - imageCenter) + vVec[0] * (j - imageCenter)
                            + volumeCenter[0];
                    pixelCoord[1] = uVec[1] * (i - imageCenter) + vVec[1] * (j - imageCenter)
                            + volumeCenter[1];
                    pixelCoord[2] = uVec[2] * (i - imageCenter) + vVec[2] * (j - imageCenter)
                            + volumeCenter[2];

                    
                double val = 0;
                voxelColor.a = 1;
                voxelColor.r = -1;
                voxelColor.g = -1;
                voxelColor.b = -1;
                
                //back to front
                for(int k = -range; k <= range; k=k+resolution){
                    short v = getVoxelTLI(pixelCoord[0]+k*viewVec[0],
                                          pixelCoord[1]+k*viewVec[1],
                                          pixelCoord[2]+k*viewVec[2]);
                    
                    double opacity = tFunc.getColor(v).a;
                    //val = v*opacity+(1-opacity)*val;
                    
                    voxelColor.r = voxelColor.r == -1 ? tFunc.getColor(v).r : tFunc.getColor(v).r * opacity + (1-opacity)*voxelColor.r;
                    voxelColor.g = voxelColor.g == -1 ? tFunc.getColor(v).g : tFunc.getColor(v).g * opacity + (1-opacity)*voxelColor.g;
                    voxelColor.b = voxelColor.b == -1 ? tFunc.getColor(v).b : tFunc.getColor(v).b * opacity + (1-opacity)*voxelColor.b;
                    
                }
                //System.out.println(val);
                //System.out.println("Final voxel : ("+voxelColor.r+","+voxelColor.g+","+voxelColor.b+","+voxelColor.a+")");
                if(voxelColor.r == 0 && voxelColor.g == 0  && voxelColor.b == 0)
                    voxelColor.a = 0;
                
                // BufferedImage expects a pixel color packed as ARGB in an int
                int c_alpha = voxelColor.a <= 1.0 ? (int) Math.floor(voxelColor.a * 255) : 255;
                int c_red = voxelColor.r <= 1.0 ? (int) Math.floor(voxelColor.r * 255) : 255;
                int c_green = voxelColor.g <= 1.0 ? (int) Math.floor(voxelColor.g * 255) : 255;
                int c_blue = voxelColor.b <= 1.0 ? (int) Math.floor(voxelColor.b * 255) : 255;
                int pixelColor = (c_alpha << 24) | (c_red << 16) | (c_green << 8) | c_blue;
                image.setRGB(i, j, pixelColor);
            }
        }
    }
    
    void transferFunction(double[] viewMatrix, int resolution, boolean forceShadingOFF) {

        // clear image
        for (int j = 0; j < image.getHeight(); j++) {
            for (int i = 0; i < image.getWidth(); i++) {
                image.setRGB(i, j, 0);
            }
        }

        // vector uVec and vVec define a plane through the origin, 
        // perpendicular to the view vector viewVec
        double[] viewVec = new double[3];
        double[] uVec = new double[3];
        double[] vVec = new double[3];
        VectorMath.setVector(viewVec, viewMatrix[2], viewMatrix[6], viewMatrix[10]);
        VectorMath.setVector(uVec, viewMatrix[0], viewMatrix[4], viewMatrix[8]);
        VectorMath.setVector(vVec, viewMatrix[1], viewMatrix[5], viewMatrix[9]);

        // image is square
        int imageCenter = image.getWidth() / 2;

        double[] pixelCoord = new double[3];
        double[] volumeCenter = new double[3];
        VectorMath.setVector(volumeCenter, volume.getDimX() / 2, volume.getDimY() / 2, volume.getDimZ() / 2);

        // sample on a plane through the origin of the volume data
        double max = volume.getMaximum();
        TFColor voxelColor = new TFColor(0,0,0,0);
        TFColor shadedColor = new TFColor(0,0,0,1);
        
        //Levoy's
        int fv = tfEditor2D.triangleWidget.baseIntensity;
        double r = tfEditor2D.triangleWidget.radius;
        TFColor triangleColor = tfEditor2D.triangleWidget.color;
        double opacity = triangleColor.a;
        
        //Phong:
        double ka = 0.1; //ambient
        double kd = 0.7; //diffuse
        double ks = 0.2; //specular
        TFColor lightColor = new TFColor(1,1,1,1); //lightcolor
        double alpha = 10.0;
        double[] lightVec= new double[3];
        double[] normalVec = new double[3];       
        VectorMath.setVector(lightVec, viewMatrix[2]+0.1, viewMatrix[6]+0.2, viewMatrix[10]);
        double[] H = new double[3];
        VectorMath.setVector(H,viewVec[0]+lightVec[0],viewVec[1]+lightVec[1],viewVec[2]+lightVec[2]);
        VectorMath.setVector(H,H[0]/VectorMath.length(H),H[1]/VectorMath.length(H),H[2]/VectorMath.length(H));
        
                
        //Calcule automatiquement la range
        int range = min4((int) (volume.getDimX() / viewVec[0]),
                        (int) (volume.getDimY() / viewVec[1]),
                        (int) (volume.getDimZ() / viewVec[2]),
                        500);
        
        for (int j = 0; j < image.getHeight(); j++) {
            for (int i = 0; i < image.getWidth(); i++) {
                    pixelCoord[0] = uVec[0] * (i - imageCenter) + vVec[0] * (j - imageCenter)
                            + volumeCenter[0];
                    pixelCoord[1] = uVec[1] * (i - imageCenter) + vVec[1] * (j - imageCenter)
                            + volumeCenter[1];
                    pixelCoord[2] = uVec[2] * (i - imageCenter) + vVec[2] * (j - imageCenter)
                            + volumeCenter[2];
                
                voxelColor.a = 1;
                voxelColor.r = -1;
                voxelColor.g = -1;
                voxelColor.b = -1;
                shadedColor.r = 0;
                shadedColor.g = 0;
                shadedColor.b = 0;
                //back to front
                for(int k = -range; k <= range; k=k+resolution){
                    
                    double x = pixelCoord[0]+k*viewVec[0];
                    double y = pixelCoord[1]+k*viewVec[1];
                    double z = pixelCoord[2]+k*viewVec[2];
                           
                    short fx1 = getVoxelTLI(x,y,z);
                    //Levoy's Method to compute opacity
                    float gfx1 = 0;
                    
                    if ((x >= 0) && (x < volume.getDimX()) && (y >= 0) && (y < volume.getDimY())
                        && (z >= 0) && (z < volume.getDimZ())) 
                    {
                        gfx1 = gradients.getGradient((int)(Math.floor(x)),(int)(Math.floor(y)),(int)(Math.floor(z))).mag;//?
                    }

                    if(gfx1==0 && fx1 == fv)
                    {
                        opacity = triangleColor.a;
                    }
                    else if(fx1 - r * gfx1 <= fv && fv <= fx1 + r * gfx1
                             && gfx1 <= tfEditor2D.triangleWidget.hmaxN*tfEditor2D.maxGradientMagnitude &&
                            gfx1 >= tfEditor2D.triangleWidget.hminN*tfEditor2D.maxGradientMagnitude 
                            )
                    {
                        opacity = triangleColor.a * (1 - 1/r * Math.abs( fv - fx1 ) / gfx1);
                    }
                    else
                    {
                        opacity = 0;
                    }
                    if(voxelColor.r == -1) voxelColor.r = triangleColor.r * opacity;
                    else voxelColor.r = triangleColor.r * opacity + (1-opacity)*voxelColor.r;
                    if(voxelColor.g == -1) voxelColor.g = triangleColor.g * opacity;
                    else voxelColor.g = triangleColor.g * opacity + (1-opacity)*voxelColor.g;
                    if(voxelColor.b == -1) voxelColor.b = triangleColor.b * opacity;
                    else voxelColor.b = triangleColor.b * opacity + (1-opacity)*voxelColor.b;
                    
                    if(shadingOn && !forceShadingOFF){
                        if ((x > 0) && (x < volume.getDimX()) && (y > 0) && (y < volume.getDimY())
                        && (z > 0) && (z < volume.getDimZ()) 
                                && opacity != 0) 
                        {
                            VectorMath.setVector(normalVec, 
                                    gradients.getGradient((int)(Math.floor(x)),(int)(Math.floor(y)),(int)(Math.floor(z))).x,
                                    gradients.getGradient((int)(Math.floor(x)),(int)(Math.floor(y)),(int)(Math.floor(z))).y,
                                    gradients.getGradient((int)(Math.floor(x)),(int)(Math.floor(y)),(int)(Math.floor(z))).z);
                            
                            if(normalVec[0]!=normalVec[0]) {
                                //??????????????? sometimes normalVec is not a number, to fix
                                shadedColor.r = 1;
                                shadedColor.g = 0;
                                shadedColor.b = 0;
                                
                            } else {
                                VectorMath.setVector(normalVec,normalVec[0]/VectorMath.length(normalVec),
                                    normalVec[1]/VectorMath.length(normalVec),
                                    normalVec[2]/VectorMath.length(normalVec));//normalize

                                double LN = VectorMath.dotproduct(lightVec, normalVec);
                                double NH = VectorMath.dotproduct(H, normalVec);
                                
                                if(LN > 0 && NH > 0){
                                    shadedColor.r = ka*voxelColor.r+(kd*LN*lightColor.r+lightColor.r*ks*Math.pow(NH,alpha));
                                    shadedColor.g = ka*voxelColor.g+(kd*LN*lightColor.g+lightColor.g*ks*Math.pow(NH,alpha));
                                    shadedColor.b = ka*voxelColor.b+(kd*LN*lightColor.b+lightColor.b*ks*Math.pow(NH,alpha));
                                } else {
                                    shadedColor.r = voxelColor.r;
                                    shadedColor.g = voxelColor.g;
                                    shadedColor.b = voxelColor.b;
                                }
                            }
//System.out.println(" vox: "+voxelColor+" shad: "+shadedColor);
                        }
                    }
                    
                }
                if(voxelColor.r == 0 && voxelColor.g == 0  && voxelColor.b == 0)
                    voxelColor.a = 0;
           
                // BufferedImage expects a pixel color packed as ARGB in an int
                int c_alpha = voxelColor.a <= 1.0 ? (int) Math.floor(voxelColor.a * 255) : 255;
                int c_red = 0;
                int c_green = 0;
                int c_blue = 1;
                if(shadingOn && !forceShadingOFF){
                    //if(shadedColor.r>0) System.out.println("s"+shadedColor);
                    c_red = shadedColor.r < 0 ? 0 : shadedColor.r <= 1.0 ? (int) Math.floor(shadedColor.r * 255) : 255;
                    c_green = shadedColor.g < 0 ? 0 : shadedColor.g <= 1.0 ? (int) Math.floor(shadedColor.g * 255) : 255;
                    c_blue = shadedColor.b < 0 ? 0 : shadedColor.b <= 1.0 ? (int) Math.floor(shadedColor.b * 255) : 255;
                    //if(shadedColor.r>0) System.out.println("rgb: "+c_red+","+c_green+","+c_blue);
                } else {
                    //System.out.println("v"+voxelColor);
                    c_red = voxelColor.r <= 1.0 ? (int) Math.floor(voxelColor.r * 255) : 255;
                    c_green = voxelColor.g <= 1.0 ? (int) Math.floor(voxelColor.g * 255) : 255;
                    c_blue = voxelColor.b <= 1.0 ? (int) Math.floor(voxelColor.b * 255) : 255;
                }
                int pixelColor = (c_alpha << 24) | (c_red << 16) | (c_green << 8) | c_blue;
                image.setRGB(i, j, pixelColor);
            }
        }
    }

    private void drawBoundingBox(GL2 gl) {
        gl.glPushAttrib(GL2.GL_CURRENT_BIT);
        gl.glDisable(GL2.GL_LIGHTING);
        gl.glColor4d(1.0, 1.0, 1.0, 1.0);
        gl.glLineWidth(1.5f);
        gl.glEnable(GL.GL_LINE_SMOOTH);
        gl.glHint(GL.GL_LINE_SMOOTH_HINT, GL.GL_NICEST);
        gl.glEnable(GL.GL_BLEND);
        gl.glBlendFunc(GL.GL_SRC_ALPHA, GL.GL_ONE_MINUS_SRC_ALPHA);

        gl.glBegin(GL.GL_LINE_LOOP);
        gl.glVertex3d(-volume.getDimX() / 2.0, -volume.getDimY() / 2.0, volume.getDimZ() / 2.0);
        gl.glVertex3d(-volume.getDimX() / 2.0, volume.getDimY() / 2.0, volume.getDimZ() / 2.0);
        gl.glVertex3d(volume.getDimX() / 2.0, volume.getDimY() / 2.0, volume.getDimZ() / 2.0);
        gl.glVertex3d(volume.getDimX() / 2.0, -volume.getDimY() / 2.0, volume.getDimZ() / 2.0);
        gl.glEnd();

        gl.glBegin(GL.GL_LINE_LOOP);
        gl.glVertex3d(-volume.getDimX() / 2.0, -volume.getDimY() / 2.0, -volume.getDimZ() / 2.0);
        gl.glVertex3d(-volume.getDimX() / 2.0, volume.getDimY() / 2.0, -volume.getDimZ() / 2.0);
        gl.glVertex3d(volume.getDimX() / 2.0, volume.getDimY() / 2.0, -volume.getDimZ() / 2.0);
        gl.glVertex3d(volume.getDimX() / 2.0, -volume.getDimY() / 2.0, -volume.getDimZ() / 2.0);
        gl.glEnd();

        gl.glBegin(GL.GL_LINE_LOOP);
        gl.glVertex3d(volume.getDimX() / 2.0, -volume.getDimY() / 2.0, -volume.getDimZ() / 2.0);
        gl.glVertex3d(volume.getDimX() / 2.0, -volume.getDimY() / 2.0, volume.getDimZ() / 2.0);
        gl.glVertex3d(volume.getDimX() / 2.0, volume.getDimY() / 2.0, volume.getDimZ() / 2.0);
        gl.glVertex3d(volume.getDimX() / 2.0, volume.getDimY() / 2.0, -volume.getDimZ() / 2.0);
        gl.glEnd();

        gl.glBegin(GL.GL_LINE_LOOP);
        gl.glVertex3d(-volume.getDimX() / 2.0, -volume.getDimY() / 2.0, -volume.getDimZ() / 2.0);
        gl.glVertex3d(-volume.getDimX() / 2.0, -volume.getDimY() / 2.0, volume.getDimZ() / 2.0);
        gl.glVertex3d(-volume.getDimX() / 2.0, volume.getDimY() / 2.0, volume.getDimZ() / 2.0);
        gl.glVertex3d(-volume.getDimX() / 2.0, volume.getDimY() / 2.0, -volume.getDimZ() / 2.0);
        gl.glEnd();

        gl.glBegin(GL.GL_LINE_LOOP);
        gl.glVertex3d(-volume.getDimX() / 2.0, volume.getDimY() / 2.0, -volume.getDimZ() / 2.0);
        gl.glVertex3d(-volume.getDimX() / 2.0, volume.getDimY() / 2.0, volume.getDimZ() / 2.0);
        gl.glVertex3d(volume.getDimX() / 2.0, volume.getDimY() / 2.0, volume.getDimZ() / 2.0);
        gl.glVertex3d(volume.getDimX() / 2.0, volume.getDimY() / 2.0, -volume.getDimZ() / 2.0);
        gl.glEnd();

        gl.glBegin(GL.GL_LINE_LOOP);
        gl.glVertex3d(-volume.getDimX() / 2.0, -volume.getDimY() / 2.0, -volume.getDimZ() / 2.0);
        gl.glVertex3d(-volume.getDimX() / 2.0, -volume.getDimY() / 2.0, volume.getDimZ() / 2.0);
        gl.glVertex3d(volume.getDimX() / 2.0, -volume.getDimY() / 2.0, volume.getDimZ() / 2.0);
        gl.glVertex3d(volume.getDimX() / 2.0, -volume.getDimY() / 2.0, -volume.getDimZ() / 2.0);
        gl.glEnd();

        gl.glDisable(GL.GL_LINE_SMOOTH);
        gl.glDisable(GL.GL_BLEND);
        gl.glEnable(GL2.GL_LIGHTING);
        gl.glPopAttrib();

    }

    //Visualization variables
    private int previousChoice = 0;
    public int visuChoice = 0; //0 = slicer
                               //1 = MIP
                               //2 = Compositing
                               //3 = 2D transfer function
    public boolean shadingOn = false; // Volume shading
    
    @Override
    public void visualize(GL2 gl) {


        if (volume == null) {
            return;
        }

        drawBoundingBox(gl);

        gl.glGetDoublev(GL2.GL_MODELVIEW_MATRIX, viewMatrix, 0);

        long startTime = System.currentTimeMillis();
        //----------------------------------------------------------------
        //refreshview is called when the user stops moving the mouse and/or click the image
        if(refreshView())
            switch(visuChoice){
                case 1 :
                    mip(viewMatrix,1);
                    break;
                case 2 :
                    compositing(viewMatrix,1);
                    break;
                case 3 :
                    transferFunction(viewMatrix,1,false);
                    break;
                default :
                    slicer(viewMatrix);
                    break;
            }
        else
            switch(visuChoice){
                case 1 :
                    mip(viewMatrix,15);
                    break;
                case 2 :
                    compositing(viewMatrix,15);
                    break;
                case 3 :
                    transferFunction(viewMatrix,15,true);
                    break;
                default :
                    slicer(viewMatrix);
                    break;
            }
        previousViewMatrix[0] = viewMatrix[0];
        previousViewMatrix[1] = viewMatrix[1];
        previousViewMatrix[2] = viewMatrix[2];
        previousChoice = visuChoice;
        //----------------------------------------------------------------
        
        long endTime = System.currentTimeMillis();
        double runningTime = (endTime - startTime);
        panel.setSpeedLabel(Double.toString(runningTime),(runningTime >= refreshTimeLimit));

        Texture texture = AWTTextureIO.newTexture(gl.getGLProfile(), image, false);

        gl.glPushAttrib(GL2.GL_LIGHTING_BIT);
        gl.glDisable(GL2.GL_LIGHTING);
        gl.glEnable(GL.GL_BLEND);
        gl.glBlendFunc(GL.GL_SRC_ALPHA, GL.GL_ONE_MINUS_SRC_ALPHA);

        // draw rendered image as a billboard texture
        texture.enable(gl);
        texture.bind(gl);
        double halfWidth = image.getWidth() / 2.0;
        gl.glPushMatrix();
        gl.glLoadIdentity();
        gl.glBegin(GL2.GL_QUADS);
        gl.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
        gl.glTexCoord2d(0.0, 0.0);
        gl.glVertex3d(-halfWidth, -halfWidth, 0.0);
        gl.glTexCoord2d(0.0, 1.0);
        gl.glVertex3d(-halfWidth, halfWidth, 0.0);
        gl.glTexCoord2d(1.0, 1.0);
        gl.glVertex3d(halfWidth, halfWidth, 0.0);
        gl.glTexCoord2d(1.0, 0.0);
        gl.glVertex3d(halfWidth, -halfWidth, 0.0);
        gl.glEnd();
        texture.disable(gl);
        texture.destroy(gl);
        gl.glPopMatrix();

        gl.glPopAttrib();


        if (gl.glGetError() > 0) {
            System.out.println("some OpenGL error: " + gl.glGetError());
        }

    }
    private BufferedImage image;
    private double[] viewMatrix = new double[4 * 4];
    private double[] previousViewMatrix = new double[4 * 4];
    
    private int refreshTimeLimit = 200; //in ms

    @Override
    public void changed() {
        for (int i=0; i < listeners.size(); i++) {
            listeners.get(i).changed();
        }
    }

    private boolean refreshView() {
        return (previousViewMatrix[0]==viewMatrix[0] && 
                previousViewMatrix[1]==viewMatrix[1] && 
                previousViewMatrix[2]==viewMatrix[2]) ||
                previousChoice != visuChoice;
    }
}
