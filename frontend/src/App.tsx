import { Navigate, Route, Routes } from 'react-router-dom'

import { AppLayout } from '@/components/layout/AppLayout'
import { LoginPage } from '@/features/auth/LoginPage'
import { ProtectedRoute } from '@/features/auth/components/ProtectedRoute'
import { ProgressPage } from '@/features/progress/ProgressPage'
import { ProjectsPage } from '@/features/projects/ProjectsPage'
import { ReviewSessionPage } from '@/features/review-session/ReviewSessionPage'
import { ScopePage } from '@/features/scope/ScopePage'
import { SettingsPage } from '@/features/settings/SettingsPage'
import { TodayPage } from '@/features/today/TodayPage'

function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route element={<ProtectedRoute />}>
        <Route element={<AppLayout />}>
          <Route index element={<Navigate to="/today" replace />} />
          <Route path="/today" element={<TodayPage />} />
          <Route path="/scope" element={<ScopePage />} />
          <Route path="/review/session" element={<ReviewSessionPage />} />
          <Route path="/projects" element={<ProjectsPage />} />
          <Route path="/progress" element={<ProgressPage />} />
          <Route path="/settings" element={<SettingsPage />} />
        </Route>
      </Route>
      <Route path="*" element={<Navigate to="/today" replace />} />
    </Routes>
  )
}

export default App
