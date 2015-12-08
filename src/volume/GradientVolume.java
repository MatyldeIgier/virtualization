/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package volume;

/**
 *
 * @author michel
 */
public class GradientVolume {

    public GradientVolume(Volume vol) {
        volume = vol;
        dimX = vol.getDimX();
        dimY = vol.getDimY();
        dimZ = vol.getDimZ();
        data = new VoxelGradient[dimX * dimY * dimZ];
        compute();
        maxmag = -1.0;
    }

    public VoxelGradient getGradient(int x, int y, int z) {
        return data[x + dimX * (y + dimY * z)];
    }

    
    public void setGradient(int x, int y, int z, VoxelGradient value) {
        data[x + dimX * (y + dimY * z)] = value;
    }

    public void setVoxel(int i, VoxelGradient value) {
        data[i] = value;
    }

    public VoxelGradient getVoxel(int i) {
        return data[i];
    }

    public int getDimX() {
        return dimX;
    }

    public int getDimY() {
        return dimY;
    }

    public int getDimZ() {
        return dimZ;
    }

    private void compute() {
        System.out.println("Computing...");
        for (int i=0; i<data.length; i++) {
            int dx = 0, dy= 0, dz= 0;
            int x = i%dimX;
            int y = (i%(dimX*dimY)-i%dimX)/dimX;
            int z = (i-i%dimX-(i%(dimX*dimY)-i%dimX))/(dimX*dimY);
            if (x-1 <= 0 || x+1 >= volume.getDimX()) 
                dx=0;
            else
            {
                if (y-1 <= 0 || y+1 >= volume.getDimY()) 
                    dy=0;
                else
                { 
                    if (z-1 <= 0 || z+1 >= volume.getDimZ()) 
                        dz=0;
                    else
                    {
                        dz = volume.getVoxel(x,y,z+1)-volume.getVoxel(x,y,z-1);
                        dy = volume.getVoxel(x,y+1,z)-volume.getVoxel(x,y-1,z);
                        dx = volume.getVoxel(x+1,y,z)-volume.getVoxel(x-1,y,z);
                    }
                    
                }
                
            }
            data[i] = new VoxelGradient(dx,dy,dz);
        }
                
    }
    
    public double getMaxGradientMagnitude() {
        if (maxmag >= 0) {
            return maxmag;
        } else {
            double magnitude = data[0].mag;
            for (int i=0; i<data.length; i++) {
                magnitude = data[i].mag > magnitude ? data[i].mag : magnitude;
            }   
            maxmag = magnitude;
            return magnitude;
        }
    }
    
    private int dimX, dimY, dimZ;
    private VoxelGradient zero = new VoxelGradient();
    VoxelGradient[] data;
    Volume volume;
    double maxmag;
}
