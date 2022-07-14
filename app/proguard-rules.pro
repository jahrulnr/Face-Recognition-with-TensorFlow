-assumenosideeffects class android.util.Log {
  public static *** v(...);
  public static *** i(...);
}

-assumenosideeffects class java.io.PrintStream {
     public void println(%);
     public void println(**);
 }

-keep class com.jahrulnr.facerecognition.inc.Recognition { *; }