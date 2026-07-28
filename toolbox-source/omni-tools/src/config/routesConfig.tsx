import { RouteObject } from 'react-router-dom';
import PlatformReturn from '../components/PlatformReturn';

const routes: RouteObject[] = [
  {
    path: '*',
    element: <PlatformReturn />
  }
];

export default routes;
