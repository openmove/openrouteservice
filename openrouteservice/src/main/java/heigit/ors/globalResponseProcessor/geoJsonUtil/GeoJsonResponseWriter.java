package heigit.ors.globalResponseProcessor.geoJsonUtil;


import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.LineString;
import heigit.ors.exceptions.StatusCodeException;
import heigit.ors.isochrones.IsochroneRequest;
import heigit.ors.routing.RouteResult;
import heigit.ors.routing.RoutingRequest;
import heigit.ors.services.routing.requestprocessors.json.JsonRoutingResponseWriter;
import org.geotools.feature.DefaultFeatureCollection;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.geojson.feature.FeatureJSON;
import org.json.JSONArray;
import org.json.JSONObject;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;

import java.io.IOException;
import java.io.StringWriter;
import java.util.HashMap;

/**
 * The {@link GeoJsonResponseWriter} class handles the global GeoJSON export or should do this in the future.
 * The idea is to write easy to use code that can/should be reused as much as possible.
 * "homegrown" export solutions, that anyway use the same export ideas, can be combined this way.
 * The general access to this class should be through a toGeoJson function that does the magic.
 *
 * @author Julian Psotta, julian@openrouteservice.org
 */
public class GeoJsonResponseWriter {

    /**
     * The function transforms {@link RouteResult}'s in a ready-to-be-shipped {@link DefaultFeatureCollection} enriched with ORS specific information
     * The return value is an unstructured {@link JSONObject} per definition. Don't expect good readability.
     *
     * @param rreq         A {@link RoutingRequest} holding the initial Request.
     * @param routeResult A {@link RouteResult[]}.
     * @return It will always return a {@link DefaultFeatureCollection} in a {@link JSONObject} representation.
     * @throws Exception Throws an error if the JsonRoute could not be calculated
     */
    public static JSONObject toGeoJson(RoutingRequest rreq, RouteResult[] routeResult) throws Exception {

        // Create HashMap of HashMaps to store properties for individual Features in it, accessible through unique identifiers
        HashMap<String, HashMap<String, JSONArray>> featurePropertiesMap = new HashMap<>();
        // Create HashMap to store FeatureCollection properties. No identifier necessary because there will be just one FeatureCollection at a time
        HashMap<String, Object> defaultFeatureCollectionProperties = new HashMap<>();
        // Create GeometryFactory for reuse purposes
        GeometryFactory geometryFactory = new GeometryFactory();
        // Create a new SimpleFeatureType to create a SimpleFeature from it
        // its written capital because a custom SimpleFeatureType is a static and immutable object once created
        SimpleFeatureType ROUTINGFEATURETYPE = SimpleFeatureTypes.createRouteFeatureType();
        // Create DefaultFeatureCollection to store the SimpleFeature
        DefaultFeatureCollection defaultFeatureCollection = new DefaultFeatureCollection("routing", ROUTINGFEATURETYPE);
        // Create a SimpleFeature for GEOJSON export preparation
        SimpleFeature routingFeature = null;
        // SimpleFeature routingFeature2 = null;
        // TODO misleading thought to access the RouteResult instead of the JsonRoute!!!
        // Calculate a route to extract the JSONObject's from it
        JSONObject jRoute = JsonRoutingResponseWriter.toJson(rreq, routeResult);
        for (RouteResult route : routeResult) {
            // Create a HashMap for the individual feature properties
            HashMap<String, JSONArray> featureProperties = new HashMap<>();
            // Get the route as LineString
            LineString lineString = geometryFactory.createLineString(route.getGeometry());
            // Create a SimpleFeature from the ROUTINGFEATURETYPE template
            SimpleFeatureBuilder routingFeatureBuilder = new SimpleFeatureBuilder(ROUTINGFEATURETYPE);
            // Add Geometry
            routingFeatureBuilder.set("geometry", lineString);
            // Add BBox
            featureProperties.put("bbox", jRoute.getJSONArray("routes").getJSONObject(0).getJSONArray("bbox"));
            // Add Way_Points
            featureProperties.put("way_points", jRoute.getJSONArray("routes").getJSONObject(0).getJSONArray("way_points"));
            // Add Segments
            featureProperties.put("segments", jRoute.getJSONArray("routes").getJSONObject(0).getJSONArray("segments"));
            // Build the SimpleFeature
            routingFeature = routingFeatureBuilder.buildFeature(null);
            defaultFeatureCollection.add(routingFeature);
            featurePropertiesMap.put(routingFeature.getID(), featureProperties);
        }

        // Add the feature properties through a generalized class
        // TODO Add general addProperties here that does all the magic itseld and just returns the ready to use JSONObject
        //JSONObject geoJSON2 = addProperties(routingFeature, featurePropertiesMap);
        defaultFeatureCollectionProperties.put("bbox", jRoute.get("bbox"));
        defaultFeatureCollectionProperties.put("info", jRoute.get("info"));
        //System.out.print(featureCollectionAsJSON);

        // LineString lineString = jsonTest.readLine(reader);
        // Integrate creation of GeoJSONs into GeometryJSON.class. Can be done for each object to provide generified conversions
        // JSONObject internalGeoJson = GeometryJSON.toGeoJSON(lineString);

        //
        // TODO: Write function to add "properties" to the geojson as an optional route feature that is only available for routes
        return addProperties(defaultFeatureCollection, featurePropertiesMap, defaultFeatureCollectionProperties);

    }

    /**
     * This is an example class and not yet integrated. It is reflecting the way additional GeoJSON exports should be integrated.
     *
     * @param rreq         A {@link IsochroneRequest}.
     * @param routeResults A {@link RouteResult[]}.
     * @return It will always return a {@link DefaultFeatureCollection} in a {@link JSONObject} representation.
     */
    protected JSONObject toGeoJson(IsochroneRequest rreq, RouteResult[] routeResults) {
        return null;
    }

    /**
     * The function works as a simple access to add Properties to a single {@link SimpleFeature} from a {@link HashMap}.
     *
     * @param simpleFeature        A single and simple {@link SimpleFeature}.
     * @param featurePropertiesMap A {@link HashMap} in a {@link HashMap} ({@link HashMap} inception ;) holding the properties for each {@link SimpleFeature} referenced by a FeatureID. E.g. HashMap<String FeatureID, HashMap<String key, JSONArray value>>.
     * @return A complete {@link SimpleFeature} in a {@link JSONObject} representation with all necessary information will be returned
     * @throws IOException Throws an {@link IOException} if the {@link FeatureJSON} could not be processed.
     */
    private static JSONObject addProperties(SimpleFeature simpleFeature, HashMap<String, HashMap<String, JSONArray>> featurePropertiesMap) throws IOException {
        FeatureJSON fjson = new FeatureJSON();
        StringWriter stringWriter = new StringWriter();
        fjson.writeFeature(simpleFeature, stringWriter);
        JSONObject featureAsJSON = new JSONObject(stringWriter.toString());


        return FeatureProperties(featureAsJSON, featurePropertiesMap);
    }

    /**
     * The function works as a simple access to add Properties to each {@link SimpleFeature} in a {@link DefaultFeatureCollection} and the surrounding {@link DefaultFeatureCollection} from {@link HashMap}'s.
     * The featurePropertiesMap is accessible through FeatureIDs.
     *
     * @param defaultFeatureCollection           {@link DefaultFeatureCollection} holding a set of {@link SimpleFeature} elements.
     * @param featurePropertiesMap               A {@link HashMap} in a {@link HashMap} ({@link HashMap} inception ;) holding the properties for each {@link SimpleFeature} referenced by a FeatureID. E.g. HashMap<String FeatureID, HashMap<String key, JSONArray value>>.
     * @param defaultFeatureCollectionProperties A simple {@link HashMap} holding additional information that should be added to the root of the {@link DefaultFeatureCollection}.
     * @return A complete {@link DefaultFeatureCollection} in a {@link JSONObject} representation with all necessary information will be returned
     * @throws Exception
     */
    private static JSONObject addProperties(DefaultFeatureCollection defaultFeatureCollection, HashMap<String, HashMap<String, JSONArray>> featurePropertiesMap, HashMap<String, Object> defaultFeatureCollectionProperties) throws Exception {
        // Create feature JSON
        FeatureJSON fjson = new FeatureJSON();
        // Create the StringWriter to catch the JSON output
        StringWriter stringWriter = new StringWriter();
        // convert the geojson element to a JSONObject in a string representation using the FeatureJSON
        fjson.writeFeatureCollection(defaultFeatureCollection, stringWriter);
        JSONObject featureCollectionAsJSON = new JSONObject(stringWriter.getBuffer());

        JSONObject addFeatureProperties = FeatureProperties(featureCollectionAsJSON, featurePropertiesMap);

        return FeatureCollectionProperties(addFeatureProperties, defaultFeatureCollectionProperties);
    }

    /**
     * The function adds the properties from the featurePropertiesMap to each {@link SimpleFeature} stored in the {@link JSONObject} representation.
     * For that the {@link JSONObject} must be a {@link SimpleFeature} or {@link DefaultFeatureCollection} in {@link JSONObject} representation.
     * The Properties must be stored in a {@link HashMap} in a {@link HashMap}. E.g. HashMap<String FeatureID, HashMap<String key, JSONArray value>>.
     * The method FeatureProperties compares the FeatureIDs of the {@link DefaultFeatureCollection} or the {@link SimpleFeature} with those stores in the HashMap.
     * If matching FeatureIDs are found, the key value pairs are added to the property part of each {@link SimpleFeature}.
     *
     * @param featureOrFeatureCollection The input must to be a single {@link SimpleFeature} or a collection of {@link SimpleFeature} in a {@link DefaultFeatureCollection} represented by a {@link JSONObject}.
     * @param featurePropertiesMap       The input must be a {@link HashMap} in a {@link HashMap}. E.g. HashMap<String FeatureID, HashMap<String key, JSONArray value>>.
     * @return A single {@link SimpleFeature} or a collection of {@link SimpleFeature} in a {@link DefaultFeatureCollection} enriched with {@link SimpleFeature} properties will be returned.
     * @throws Exception
     */
    private static JSONObject FeatureProperties(JSONObject featureOrFeatureCollection, HashMap<String, HashMap<String, JSONArray>> featurePropertiesMap) {
        // Check if the the JSONObject is a FeatureCollection
        if (featureOrFeatureCollection.get("type").equals("FeatureCollection")) {
            for (int featureCount = 0; featureCount < featureOrFeatureCollection.getJSONArray("features").length(); featureCount++) {
                // Get Feature Id to look for it in the featureProperties
                String featureId = featureOrFeatureCollection.getJSONArray("features").getJSONObject(featureCount).get("id").toString();
                // Look for the feature Id
                if (featurePropertiesMap.containsKey(featureId)) {
                    // Get properties
                    HashMap<String, JSONArray> featureProperties = featurePropertiesMap.get(featureId);
                    // set properties
                    for (String key : featureProperties.keySet()) {
                        // Get the specific value by key
                        JSONArray value = featureProperties.get(key);
                        featureOrFeatureCollection.getJSONArray("features").getJSONObject(featureCount).getJSONObject("properties").put(key, value);
                    }
                }
            }
        }
        // Check if the JSONObject is a SimpleFeature
        else if (featureOrFeatureCollection.get("type").equals("Feature")) {
            // Get Feature Id to look for it in the featureProperties
            String featureId = featureOrFeatureCollection.get("id").toString();
            // Look for the feature Id
            if (featurePropertiesMap.containsKey(featureId)) {
                // Get properties
                HashMap<String, JSONArray> featureProperties = featurePropertiesMap.get(featureId);
                // Iterate over the HashMap and add each property to the Feature
                for (String key : featureProperties.keySet()) {
                    // Get the specific value by key
                    JSONArray value = featureProperties.get(key);
                    featureOrFeatureCollection.getJSONObject("properties").put(key, value);
                }
            }
        }
        return featureOrFeatureCollection;
    }

    /**
     * The function adds the properties from the featureCollectionProperties to the {@link DefaultFeatureCollection} in {@link JSONObject} representation.
     * For that the {@link JSONObject} must be a {@link DefaultFeatureCollection} in {@link JSONObject} representation.
     * The Properties must be stored in a simple {@link HashMap}. E.g. HashMap<String key, JSONArray value>.
     * The method adds every key value pair from the {@link HashMap} to the root of the {@link DefaultFeatureCollection}.
     *
     * @param featureCollection           The input must to be a {@link DefaultFeatureCollection} in a {@link JSONObject} representation
     * @param featureCollectionProperties The input must be a {@link HashMap}. E.g. HashMap<String key, JSONArray value>.
     * @return A {@link DefaultFeatureCollection} in {@link JSONObject} representation, enriched with properties, will be returned.
     * @throws Exception
     */
    private static JSONObject FeatureCollectionProperties(JSONObject featureCollection, HashMap<String, Object> featureCollectionProperties) {
        // Check if the JSONObject is a FeatureCollection
        if (featureCollection.get("type").equals("FeatureCollection")) {
            // Iterate over HashMap and add each Property to the FeatureCollection
            for (String key : featureCollectionProperties.keySet()
                    ) {
                // Get the specific value by key
                Object value = featureCollectionProperties.get(key);
                featureCollection.put(key, value);
            }
        }
        return featureCollection;
    }


}
