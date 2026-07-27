package com.dndmaster.aigamemaster.application.ports;
public interface MapModelPort{MapOutput generate(MapInput input);record MapInput(String selectedScenario,String currentContext){}record MapOutput(int width,int height,String structuredLayers){}}
