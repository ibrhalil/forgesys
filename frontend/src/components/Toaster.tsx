import { ToastContainer } from 'react-toastify';
import 'react-toastify/dist/ReactToastify.css';

/**
 * Single global toast container, mounted once at the app root (outside the
 * {@link ErrorBoundary} so it survives a render crash). Light theme matches the
 * app's light corporate surface.
 */
export function Toaster() {
  return (
    <ToastContainer
      theme="light"
      position="top-right"
      autoClose={4000}
      hideProgressBar={false}
      newestOnTop
      closeOnClick
      pauseOnHover
      pauseOnFocusLoss
      draggable
    />
  );
}
