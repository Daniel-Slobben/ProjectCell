package slobben.cells.service;

public class NativeGeneration {
    // Load native library
    static {
        System.loadLibrary("nativegen");
    }

    static void main(String[] args) {
        new NativeGeneration().generate();
    }

    // Declare native method
    public native void generate();
}
