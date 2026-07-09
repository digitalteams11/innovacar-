export interface GpsFieldGuideEntry {
  title: string;
  text: string;
  whereToFind?: string;
  example?: string;
  security?: string;
  usage?: string;
}

export const GPS_FIELD_GUIDE: Record<string, GpsFieldGuideEntry> = {
  provider: {
    title: 'GPS Provider',
    text: 'Choose the platform where your GPS devices are managed. If your cars already have GPS devices installed, ask your GPS supplier which platform they use.',
    example: 'IOPGPS, Traccar, Wialon, GPSWOX, Custom API',
    usage: 'RentCar uses this provider to read vehicle location and sync devices automatically.',
  },
  appId: {
    title: 'APP ID / Account ID',
    text: 'This is the account identifier from your GPS platform. Some providers call it APP ID, Account ID, Client ID, or Username ID.',
    whereToFind: 'Open your GPS provider dashboard → API / Developer / Integration settings.',
    example: 'app_123456, account_98765, company_demo',
  },
  apiKey: {
    title: 'API Key',
    text: 'This is the secret key that allows RentCar to connect to your GPS platform on your behalf.',
    whereToFind: 'GPS provider dashboard → API Keys / Developer Settings → Generate API Key.',
    security: 'Never share this key publicly. RentCar encrypts it before storing it and never displays it again in full — only a masked value is shown.',
  },
  baseUrl: {
    title: 'Base URL',
    text: 'This is the API server URL of your GPS provider.',
    example: 'https://api.iopgps.com, https://demo.traccar.org/api, https://hst-api.wialon.com',
    whereToFind: 'If you do not know it, ask your GPS provider or check their API documentation.',
  },
  deviceGroupId: {
    title: 'Device Group ID',
    text: 'Optional. Some GPS platforms organize devices inside groups or fleets. If your provider gives you a fleet/group ID, enter it here to limit sync to that group.',
    example: 'fleet_001, group_12',
    usage: 'If your provider does not use groups, leave this field empty — RentCar will sync all available devices.',
  },
  webhookUrl: {
    title: 'Webhook URL',
    text: 'Optional. Some GPS providers can be configured to send location updates automatically instead of RentCar pulling data on a schedule.',
    whereToFind: 'Paste the URL given by your GPS provider, or contact RentCar support if your provider requires an inbound endpoint.',
    usage: 'If your provider does not support webhooks, leave this field empty — RentCar will still sync via Test Connection / Sync Devices.',
  },
  testConnection: {
    title: 'Test Connection',
    text: 'After entering your credentials, click Test Connection. RentCar will try to connect to your GPS provider and check whether devices can be loaded.',
    usage: 'Success means your provider is reachable and authenticated. Failure means you should re-check your API key, base URL, and account ID against your provider\'s documentation.',
  },
  deviceMapping: {
    title: 'Device Mapping',
    text: 'After a successful sync, RentCar lists the GPS devices found on your provider account. Link each device to the correct vehicle in RentCar so its live position appears correctly.',
    example: 'Device IMEI 864123456789123 → Vehicle Dacia Logan 123-A-45',
  },
  vehicleLinking: {
    title: 'Vehicle Linking',
    text: 'Each GPS device should be linked to exactly one vehicle. Linking is what allows RentCar to show live location for that vehicle on the vehicle page, GPS map, and dashboard.',
  },
  lastSync: {
    title: 'Last Sync',
    text: 'Shows the last time RentCar successfully pulled GPS data from your provider. If this is old or empty, click Sync Devices or Test Connection.',
  },
  activeDevices: {
    title: 'Active Devices',
    text: 'Number of GPS devices detected on your provider account during the last successful sync.',
  },
  credentials: {
    title: 'Credentials Security',
    text: 'Your API key is sensitive. RentCar never displays it fully after it has been saved — only a masked indicator ("Stored securely") is shown. The key itself is encrypted at rest (AES-256) and is never logged.',
    security: 'If you need to change your API key, simply type a new value — leaving the field blank keeps the existing stored key unchanged.',
  },
};

export interface ProviderGuide {
  label: string;
  notes: string[];
}

export const GPS_PROVIDER_GUIDE: Record<string, ProviderGuide> = {
  IOPGPS: {
    label: 'IOPGPS',
    notes: [
      'APP ID is the account name shown in your IOPGPS API access page.',
      'API Key (password) is generated inside the IOPGPS dashboard under API access.',
      'Base URL is usually https://www.iopgps.com unless your provider gave you a different regional endpoint.',
      'If unsure, contact IOPGPS support and ask for API access credentials.',
    ],
  },
  TRACCAR: {
    label: 'Traccar',
    notes: [
      'Traccar uses a server URL plus an API token (or username/password depending on your server setup).',
      'Base URL example: https://your-traccar-server.com/api',
      'Device IDs and unique identifiers (IMEI) are visible on the Traccar "Devices" page.',
    ],
  },
  WIALON: {
    label: 'Wialon',
    notes: [
      'Wialon uses a token/session-based API rather than a simple API key.',
      'Generate an API token from your Wialon account under "Token management".',
      'Device IDs ("units") can be found in the Wialon units list.',
    ],
  },
  GPSWOX: {
    label: 'GPSWOX',
    notes: [
      'GPSWOX provides both the API endpoint and the API key from its admin panel.',
      'After a successful connection, the device list can be synced automatically.',
    ],
  },
  CUSTOM: {
    label: 'Custom API',
    notes: [
      'Use this option if your GPS supplier provides a custom/proprietary API.',
      'You must enter both the Base URL and the API Key — RentCar will call the configured endpoint to verify reachability.',
      'Device sync for custom providers may require additional integration work; contact RentCar support for assistance.',
    ],
  },
};

export const GPS_CONNECT_STEPS: string[] = [
  'Choose your GPS provider.',
  'Open your GPS provider dashboard.',
  'Go to API / Developer / Integration settings.',
  'Copy your APP ID / Account ID.',
  'Generate or copy your API Key.',
  'Enter the Base URL for your provider.',
  'Click Save Configuration.',
  'Click Test Connection.',
  'Link each GPS device to a vehicle.',
  'Open the GPS map to verify live tracking.',
];
