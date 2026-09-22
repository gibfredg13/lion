# Phase 1 Frontend Rebranding Changes

## Overview
This document summarizes the changes made to transition the frontend from 'HackMerlin' / 'ING Security Challenge' to 'The Lion's Den', replacing 'Merlin' with 'Leo' (the professional friendly lion) and removing any banking references.

## Files Modified

### 1. `/home/admin/Merlin/hackmerlin.io/frontend/src/components/Navigation.tsx`
- **What changed:** Updated the navigation title from `🏦 ING Security Challenge` to `🦁 The Lion's Den`. Replaced the bank emoji with the lion emoji. Wrapped the Admin link in a conditional rendering block based on `session.data?.isAdmin`.
- **Why:** To rebrand the top navigation bar and restrict admin access link visibility to authorized users only.

### 2. `/home/admin/Merlin/hackmerlin.io/frontend/src/components/LoginPage.tsx`
- **What changed:** Maintained the title `🦁 Lion's Quest` (as requested to keep) but changed the subtitle from `Challenge Your Courage` to `Enter The Lion's Den`.
- **Why:** Ensure a consistent branding experience on the login page while avoiding ING/Bank terms.

### 3. `/home/admin/Merlin/hackmerlin.io/frontend/src/components/RegisterPage.tsx`
- **What changed:** Added an `Event Access Code` input field that updates an `accessCode` state and sends it in the `POST /api/auth/register` payload. Added validation to prevent blank access codes. Updated the title to `🦁 The Lion's Den` and subtitle to `Join the Challenge`.
- **Why:** The event organizer requires an access code for registration to restrict signups. Branding was updated to match the new theme.

### 4. `/home/admin/Merlin/hackmerlin.io/frontend/src/components/Leaderboard.tsx`
- **What changed:** Updated the page title to `🏆 The Lion's Den — Leaderboard` and subtitle to `Who can outsmart Leo?`. Added a new `Score` column in the table, calculated client-side as `Math.round(entry.maxLevelReached * 50 + Math.max(0, 300 - entry.durationSeconds) / 10)`. Replaced the `Avg Level` stat card with `Most Levels Cracked`, showing the maximum level reached.
- **Why:** Rebranded the leaderboard to match the lion theme and provided an immediate client-side score approximation. The stat card update provides a more relevant metric for a hackathon.

### 5. `/home/admin/Merlin/hackmerlin.io/frontend/src/components/AdminDashboard.tsx`
- **What changed:** Changed the header title to `🦁 The Lion's Den — Admin` and subtitle to `Real-time event monitoring and control`. Added a new `⚡ Live Stats` tab that fetches live metrics (`active-users`, `token-stats`, `dgx-health`, `level-stats`) every 5 seconds. Added toggle controls for enabling/disabling levels.
- **Why:** Rebrand the admin panel and provide real-time monitoring and event control for organizers.

### 6. `/home/admin/Merlin/hackmerlin.io/frontend/index.html`
- **What changed:** Updated the `<title>` and `<meta>` description tags to explicitly state `The Lion's Den — AI Security Challenge`. Replaced the SVG favicon link with a 🦁 inline data SVG.
- **Why:** Improve SEO and browser tab appearance to align with the new brand.

### 7. `/home/admin/Merlin/hackmerlin.io/frontend/src/App.tsx`
- **What changed:** Imported `LeaderboardTV` and added a new route `/leaderboard/tv` that renders `<LeaderboardTV />`.
- **Why:** Provide a route for the TV/Projector display mode. Code variables containing 'merlin' were kept as they are non-user-facing.

### 8. `/home/admin/Merlin/hackmerlin.io/frontend/src/components/LeaderboardTV.tsx`
- **What changed:** Created a new file containing the `LeaderboardTV` component.
- **Why:** Fulfills the requirement for a full-screen, highly legible leaderboard suitable for projector/TV display at the venue, featuring auto-cycling and real-time polling.
