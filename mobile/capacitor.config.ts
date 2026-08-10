import { CapacitorConfig } from '@capacitor/cli';

const config: CapacitorConfig = {
  appId: 'com.pucetec.labtime',
  appName: 'LabTime',
  webDir: 'dist',
  // Callback URL en Cognito (App client) debe apuntar a este custom scheme:
  // com.pucetec.labtime://callback
};

export default config;
