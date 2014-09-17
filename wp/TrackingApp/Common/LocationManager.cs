using Windows.Devices.Geolocation;

using TrackingApp;

namespace com.codename1.impl
{
    public class LocationManager : com.codename1.location.LocationManager
    {
        private global::System.DateTime EPOCH_START = new global::System.DateTime(1970, 1, 1, 0, 0, 0, global::System.DateTimeKind.Utc);
        private Geolocator locator;
        private Geocoordinate lastPosition;

        public LocationManager()
        {
            base.@this();
        }

        public override void bindListener()
        {
#if LOG
            CN1Extensions.Log("LocationManager.bindListener; listener? {0}", getLocationListener());
#endif
            if (locator == null)
            {
                locator = new Geolocator();
                locator.DesiredAccuracy = PositionAccuracy.High; // High = GPS
                locator.ReportInterval = 250; // 250 ms; cannot be 0 (real-time) fucking MS and their docs :(
#if LOG
                CN1Extensions.Log("LocationManager threshold: {0}", locator.MovementThreshold);
#endif
            }
            locator.StatusChanged += locator_StatusChanged;
            locator.PositionChanged += locator_PositionChanged;
        }

        public override void clearListener()
        {
#if LOG
            CN1Extensions.Log("LocationManager.clearListener; listener? {0}", getLocationListener());
#endif
            if (locator != null)
            {
                locator.StatusChanged -= locator_StatusChanged;
                locator.PositionChanged -= locator_PositionChanged;
            }
        }

        public override object getCurrentLocation()
        {
            if (lastPosition != null)
            {
                return convert(lastPosition);
            }
            return null;
        }

        public override object getLastKnownLocation()
        {
            if (lastPosition != null)
            {
                return convert(lastPosition);
            }
            return null;
        }

        void locator_StatusChanged(object sender, StatusChangedEventArgs args)
        {
#if LOG
            CN1Extensions.Log("LocationManager.statusChanged; {0}", args.Status);
#endif
            switch (args.Status)
            {
                case PositionStatus.NotInitialized:
                case PositionStatus.Disabled:
                    setStatus(_fOUT_1OF_1SERVICE);
                    break;
                case PositionStatus.Initializing:
                case PositionStatus.NoData:
                    setStatus(_fTEMPORARILY_1UNAVAILABLE);
                    break;
                case PositionStatus.Ready:
                    setStatus(_fAVAILABLE);
                    break;
            }
        }

        void locator_PositionChanged(Geolocator sender, PositionChangedEventArgs args)
        {
#if LOG
            CN1Extensions.Log("LocationManager.positionChanged; source: {0}", args.Position.Coordinate.PositionSource);
#endif
            if (getLocationListener() != null)
            {
                ((com.codename1.location.LocationListener)getLocationListener()).locationUpdated(convert(args.Position.Coordinate));
            }
        }

        private com.codename1.location.Location convert(Geocoordinate coordinate)
        {
            com.codename1.location.Location location = new com.codename1.location.Location();
            location.@this();
            location.setTimeStamp((long)(coordinate.Timestamp - EPOCH_START).TotalMilliseconds);
            location.setLatitude(coordinate.Latitude);
            location.setLongitude(coordinate.Longitude);
            location.setAccuracy((float)coordinate.Accuracy);
            if (coordinate.Altitude.HasValue) 
                location.setAltitude((double)coordinate.Altitude);
            if (coordinate.Heading.HasValue) 
                location.setDirection((float)coordinate.Heading);
            if (coordinate.Speed.HasValue) 
                location.setVelocity((float)coordinate.Speed);
            location.setStatus(getStatus());
            return location;
        }
    }
}