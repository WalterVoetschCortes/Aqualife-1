package aqua.blatt1.common.msgtypes;

import java.io.Serializable;

public class Collector implements Serializable {
    private int localfishies;
    public Collector (int localfishies) {
        this.localfishies = localfishies;
    }

    public int getLocalfishies() {
        return localfishies;
    }

    public void setLocalfishies(int localfishies) {
        this.localfishies = localfishies;
    }

}
