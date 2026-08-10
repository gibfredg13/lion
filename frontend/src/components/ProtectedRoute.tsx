import { ReactNode } from "react";
import { Navigate } from "react-router-dom";
import { useSession } from "../hooks/session";
import MerlinLoader from "./MerlinLoader";

export default function ProtectedRoute({ children }: { children: ReactNode }) {
  const session = useSession();

  if (session.isLoading) return <MerlinLoader />;

  if (!session.data?.email) {
    return <Navigate to="/login" replace />;
  }

  return children;
}
