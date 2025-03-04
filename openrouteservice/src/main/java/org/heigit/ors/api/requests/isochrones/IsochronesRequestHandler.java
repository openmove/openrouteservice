/*
 * This file is part of Openrouteservice.
 *
 * Openrouteservice is free software; you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License as published by the Free Software Foundation; either version 2.1
 * of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License along with this library;
 * if not, see <https://www.gnu.org/licenses/>.
 */

package org.heigit.ors.api.requests.isochrones;

import com.vividsolutions.jts.geom.Coordinate;
import org.heigit.ors.api.requests.common.APIEnums;
import org.heigit.ors.api.requests.common.GenericHandler;
import org.heigit.ors.api.requests.routing.RouteRequestHandler;
import org.heigit.ors.api.requests.routing.RouteRequestOptions;
import org.heigit.ors.common.DistanceUnit;
import org.heigit.ors.common.StatusCode;
import org.heigit.ors.common.TravelRangeType;
import org.heigit.ors.common.TravellerInfo;
import org.heigit.ors.exceptions.InternalServerException;
import org.heigit.ors.exceptions.ParameterOutOfRangeException;
import org.heigit.ors.exceptions.ParameterValueException;
import org.heigit.ors.exceptions.StatusCodeException;
import org.heigit.ors.isochrones.*;
import org.heigit.ors.routing.RouteSearchParameters;
import org.heigit.ors.routing.RoutingProfileManager;
import org.heigit.ors.routing.RoutingProfileType;
import org.heigit.ors.services.isochrones.IsochronesServiceSettings;
import org.heigit.ors.util.DistanceUnitUtil;

import java.util.Arrays;
import java.util.List;

import static org.heigit.ors.api.requests.isochrones.IsochronesRequestEnums.CalculationMethod.CONCAVE_BALLS;
import static org.heigit.ors.api.requests.isochrones.IsochronesRequestEnums.CalculationMethod.FASTISOCHRONE;

public class IsochronesRequestHandler extends GenericHandler {

    private IsochroneMapCollection isoMaps;
    private IsochroneRequest isochroneRequest;

    public IsochronesRequestHandler() {
        super();
        this.errorCodes.put("UNKNOWN_PARAMETER", IsochronesErrorCodes.UNKNOWN_PARAMETER);
        this.errorCodes.put("INVALID_JSON_FORMAT", IsochronesErrorCodes.INVALID_JSON_FORMAT);
        this.errorCodes.put("INVALID_PARAMETER_VALUE", IsochronesErrorCodes.INVALID_PARAMETER_VALUE);
    }

    public void generateIsochronesFromRequest(IsochronesRequest request) throws Exception {
        isochroneRequest = convertIsochroneRequest(request);
        // request object is built, now check if ors config allows all settings
        List<TravellerInfo> travellers = isochroneRequest.getTravellers();

        // TODO where should we put the validation code?
        validateAgainstConfig(isochroneRequest, travellers);

        if (!travellers.isEmpty()) {
            isoMaps = new IsochroneMapCollection();

            for (int i = 0; i < travellers.size(); ++i) {
                IsochroneSearchParameters searchParams = isochroneRequest.getSearchParameters(i);
                IsochroneMap isochroneMap = RoutingProfileManager.getInstance().buildIsochrone(searchParams);
                isoMaps.add(isochroneMap);
            }

        }
    }

    Float convertSmoothing(Double smoothingValue) throws ParameterValueException {
        float f = (float) smoothingValue.doubleValue();

        if (smoothingValue < 0 || smoothingValue > 100)
            throw new ParameterValueException(IsochronesErrorCodes.INVALID_PARAMETER_VALUE, IsochronesRequest.PARAM_SMOOTHING, smoothingValue.toString());

        return f;
    }

    String convertLocationType(IsochronesRequestEnums.LocationType locationType) throws ParameterValueException {
        IsochronesRequestEnums.LocationType value;

        switch (locationType) {
            case DESTINATION:
                value = IsochronesRequestEnums.LocationType.DESTINATION;
                break;
            case START:
                value = IsochronesRequestEnums.LocationType.START;
                break;
            default:
                throw new ParameterValueException(IsochronesErrorCodes.INVALID_PARAMETER_VALUE, IsochronesRequest.PARAM_LOCATION_TYPE, locationType.toString());
        }

        return value.toString();
    }

    TravelRangeType convertRangeType(IsochronesRequestEnums.RangeType rangeType) throws ParameterValueException {
        TravelRangeType travelRangeType;

        switch (rangeType) {
            case DISTANCE:
                travelRangeType = TravelRangeType.DISTANCE;
                break;
            case TIME:
                travelRangeType = TravelRangeType.TIME;
                break;
            default:
                throw new ParameterValueException(IsochronesErrorCodes.INVALID_PARAMETER_VALUE, IsochronesRequest.PARAM_RANGE_TYPE, rangeType.toString());
        }

        return travelRangeType;

    }

    String convertAreaUnit(APIEnums.Units unitsIn) throws ParameterValueException {

        DistanceUnit areaUnit;
        try {
            areaUnit = DistanceUnitUtil.getFromString(unitsIn.toString(), DistanceUnit.UNKNOWN);
            if (areaUnit == DistanceUnit.UNKNOWN)
                throw new ParameterValueException(IsochronesErrorCodes.INVALID_PARAMETER_VALUE, IsochronesRequest.PARAM_AREA_UNITS, unitsIn.toString());

            return DistanceUnitUtil.toString(areaUnit);

        } catch (Exception e) {
            throw new ParameterValueException(IsochronesErrorCodes.INVALID_PARAMETER_VALUE, IsochronesRequest.PARAM_AREA_UNITS, unitsIn.toString());
        }
    }

    String convertRangeUnit(APIEnums.Units unitsIn) throws ParameterValueException {

        DistanceUnit units;
        try {
            units = DistanceUnitUtil.getFromString(unitsIn.toString(), DistanceUnit.UNKNOWN);
            if (units == DistanceUnit.UNKNOWN)
                throw new ParameterValueException(IsochronesErrorCodes.INVALID_PARAMETER_VALUE, IsochronesRequest.PARAM_RANGE_UNITS, unitsIn.toString());
        } catch (Exception e) {
            throw new ParameterValueException(IsochronesErrorCodes.INVALID_PARAMETER_VALUE, IsochronesRequest.PARAM_RANGE_UNITS, unitsIn.toString());
        }
        return DistanceUnitUtil.toString(units);

    }

    Coordinate convertSingleCoordinate(Double[] coordinate) throws ParameterValueException {
        Coordinate realCoordinate;
        if (coordinate.length != 2) {
            throw new ParameterValueException(IsochronesErrorCodes.INVALID_PARAMETER_VALUE, IsochronesRequest.PARAM_LOCATIONS);
        }
        try {
            realCoordinate = new Coordinate(coordinate[0], coordinate[1]);
        } catch (Exception e) {
            throw new ParameterValueException(IsochronesErrorCodes.INVALID_PARAMETER_VALUE, IsochronesRequest.PARAM_LOCATIONS);
        }
        return realCoordinate;
    }

    IsochroneRequest convertIsochroneRequest(IsochronesRequest request) throws Exception {
        IsochroneRequest convertedIsochroneRequest = new IsochroneRequest();
        Double[][] locations = request.getLocations();

        for (int i = 0; i < request.getLocations().length; i++) {
            Double[] location = locations[i];
            TravellerInfo travellerInfo = constructTravellerInfo(location, request);
            travellerInfo.setId(Integer.toString(i));
            try {
                convertedIsochroneRequest.addTraveller(travellerInfo);
            } catch (Exception ex) {
                throw new InternalServerException(IsochronesErrorCodes.UNKNOWN, IsochronesRequest.PARAM_INTERVAL);
            }
        }
        if (request.hasId())
            convertedIsochroneRequest.setId(request.getId());
        if (request.hasRangeUnits())
            convertedIsochroneRequest.setUnits(convertRangeUnit(request.getRangeUnit()));
        if (request.hasAreaUnits())
            convertedIsochroneRequest.setAreaUnits(convertAreaUnit(request.getAreaUnit()));
        if (request.hasAttributes())
            convertedIsochroneRequest.setAttributes(convertAttributes(request.getAttributes()));
        if (request.hasSmoothing())
            convertedIsochroneRequest.setSmoothingFactor(convertSmoothing(request.getSmoothing()));
        if (request.hasIntersections())
            convertedIsochroneRequest.setIncludeIntersections(request.getIntersections());
        if(request.hasOptions())
            convertedIsochroneRequest.setCalcMethod(convertCalcMethod(CONCAVE_BALLS));
        else
            convertedIsochroneRequest.setCalcMethod(convertCalcMethod(FASTISOCHRONE));
        return convertedIsochroneRequest;

    }

    TravellerInfo constructTravellerInfo(Double[] coordinate, IsochronesRequest request) throws Exception {
        TravellerInfo travellerInfo = new TravellerInfo();

        RouteSearchParameters routeSearchParameters = constructRouteSearchParameters(request);
        travellerInfo.setRouteSearchParameters(routeSearchParameters);
        if (request.hasRangeType())
            travellerInfo.setRangeType(convertRangeType(request.getRangeType()));
        if (request.hasLocationType())
            travellerInfo.setLocationType(convertLocationType(request.getLocationType()));
        travellerInfo.setLocation(convertSingleCoordinate(coordinate));
        travellerInfo.getRanges();
        //range + interval
        if (request.getRange() == null) {
            throw new ParameterValueException(IsochronesErrorCodes.MISSING_PARAMETER, IsochronesRequest.PARAM_RANGE);
        }
        List<Double> rangeValues = request.getRange();
        Double intervalValue = request.getInterval();
        setRangeAndIntervals(travellerInfo, rangeValues, intervalValue);
        return travellerInfo;
    }

    RouteSearchParameters constructRouteSearchParameters(IsochronesRequest request) throws Exception {
        RouteSearchParameters routeSearchParameters = new RouteSearchParameters();
        int profileType;
        try {
            profileType = convertToIsochronesProfileType(request.getProfile());
        } catch (Exception e) {
            throw new ParameterValueException(IsochronesErrorCodes.INVALID_PARAMETER_VALUE, IsochronesRequest.PARAM_PROFILE);
        }

        if (profileType == RoutingProfileType.UNKNOWN)
            throw new ParameterValueException(IsochronesErrorCodes.INVALID_PARAMETER_VALUE, IsochronesRequest.PARAM_PROFILE);
        routeSearchParameters.setProfileType(profileType);

        if (request.hasOptions()) {
            routeSearchParameters = processIsochronesRequestOptions(request, routeSearchParameters);
        }
        routeSearchParameters.setConsiderTurnRestrictions(false);
        return routeSearchParameters;
    }

    RouteSearchParameters processIsochronesRequestOptions(IsochronesRequest request, RouteSearchParameters parameters) throws StatusCodeException {
        RouteRequestOptions options = request.getIsochronesOptions();
        parameters = new RouteRequestHandler().processRequestOptions(options, parameters);
        if (options.hasProfileParams())
            parameters.setProfileParams(convertParameters(options, parameters.getProfileType()));
        return parameters;
    }

    void validateAgainstConfig(IsochroneRequest isochroneRequest, List<TravellerInfo> travellers) throws StatusCodeException {

        if (!IsochronesServiceSettings.getAllowComputeArea() && isochroneRequest.hasAttribute("area"))
            throw new StatusCodeException(StatusCode.BAD_REQUEST, IsochronesErrorCodes.FEATURE_NOT_SUPPORTED, "Area computation is not enabled.");

        if (travellers.size() > IsochronesServiceSettings.getMaximumLocations())
            throw new ParameterOutOfRangeException(IsochronesErrorCodes.PARAMETER_VALUE_EXCEEDS_MAXIMUM, IsochronesRequest.PARAM_LOCATIONS, Integer.toString(travellers.size()), Integer.toString(IsochronesServiceSettings.getMaximumLocations()));

        for (TravellerInfo traveller : travellers) {
            int maxAllowedRange = IsochronesServiceSettings.getMaximumRange(traveller.getRouteSearchParameters().getProfileType(), isochroneRequest.getCalcMethod(), traveller.getRangeType());
            double maxRange = traveller.getMaximumRange();
            if (maxRange > maxAllowedRange)
                throw new ParameterOutOfRangeException(IsochronesErrorCodes.PARAMETER_VALUE_EXCEEDS_MAXIMUM, IsochronesRequest.PARAM_RANGE, Double.toString(maxRange), Integer.toString(maxAllowedRange));

            int maxIntervals = IsochronesServiceSettings.getMaximumIntervals();
            if (maxIntervals > 0 && maxIntervals < traveller.getRanges().length) {
                throw new ParameterOutOfRangeException(IsochronesErrorCodes.PARAMETER_VALUE_EXCEEDS_MINIMUM, IsochronesRequest.PARAM_INTERVAL, "Resulting number of " + traveller.getRanges().length + " isochrones exceeds maximum value of " + maxIntervals + ".");
            }
        }

    }

    void setRangeAndIntervals(TravellerInfo travellerInfo, List<Double> rangeValues, Double intervalValue) throws ParameterValueException, ParameterOutOfRangeException {
        double rangeValue = -1;
        if (rangeValues.size() == 1) {
            try {
                rangeValue = rangeValues.get(0);
                travellerInfo.setRanges(new double[]{rangeValue});
            } catch (NumberFormatException ex) {
                throw new ParameterValueException(IsochronesErrorCodes.INVALID_PARAMETER_VALUE, "range");
            }
        } else {
            double[] ranges = new double[rangeValues.size()];
            double maxRange = Double.MIN_VALUE;
            for (int i = 0; i < ranges.length; i++) {
                double dv = rangeValues.get(i);
                if (dv > maxRange)
                    maxRange = dv;
                ranges[i] = dv;
            }
            Arrays.sort(ranges);
            travellerInfo.setRanges(ranges);
        }
        // interval, only use if one range is defined

        if (rangeValues.size() == 1 && rangeValue != -1 && intervalValue != null){
            if (intervalValue > rangeValue) {
                throw new ParameterOutOfRangeException (IsochronesErrorCodes.PARAMETER_VALUE_EXCEEDS_MAXIMUM, IsochronesRequest.PARAM_INTERVAL, Double.toString(intervalValue), Double.toString(rangeValue));
            }
            travellerInfo.setRanges(rangeValue, intervalValue);
        }
    }

    String[] convertAttributes(IsochronesRequestEnums.Attributes[] attributes) {
        return convertAPIEnumListToStrings(attributes);
    }

    String convertCalcMethod(IsochronesRequestEnums.CalculationMethod bareCalcMethod) throws ParameterValueException {
        try {
            switch (bareCalcMethod) {
                case CONCAVE_BALLS:
                    return "concaveballs";
                case GRID:
                    return "grid";
                case FASTISOCHRONE:
                    return "fastisochrone";
                default:
                    return "none";
            }
        } catch (Exception ex) {
            throw new ParameterValueException(IsochronesErrorCodes.INVALID_PARAMETER_VALUE, "calc_method");
        }
    }

    protected static int convertToIsochronesProfileType(APIEnums.Profile profile) throws ParameterValueException {
        try {
            int profileFromString = RoutingProfileType.getFromString(profile.toString());
            if (profileFromString == 0) {
                throw new ParameterValueException(IsochronesErrorCodes.INVALID_PARAMETER_VALUE, "profile");
            }
            return profileFromString;
        } catch (Exception e) {
            throw new ParameterValueException(IsochronesErrorCodes.INVALID_PARAMETER_VALUE, "profile");
        }
    }

    public IsochroneMapCollection getIsoMaps() {
        return isoMaps;
    }

    public IsochroneRequest getIsochroneRequest() {
        return isochroneRequest;
    }

}
