import type { Metadata } from "next";
import { Big_Shoulders_Stencil, Archivo, IBM_Plex_Mono } from "next/font/google";
import "./globals.css";
import { Toaster } from "@/components/ui/sonner";
import SiteNav from "./components/SiteNav";

const displayStencil = Big_Shoulders_Stencil({
  variable: "--font-display",
  weight: ["600", "700", "800"],
  subsets: ["latin"],
});

const bodySans = Archivo({
  variable: "--font-body",
  subsets: ["latin"],
});

const dataMono = IBM_Plex_Mono({
  variable: "--font-mono",
  weight: ["400", "500"],
  subsets: ["latin"],
});

export const metadata: Metadata = {
  title: "Service Scheduler",
  description: "Demo UI for the Keyloop Unified Service Scheduler API",
};

export default function RootLayout({ children }: LayoutProps<"/">) {
  return (
    <html lang="en" className={`${displayStencil.variable} ${bodySans.variable} ${dataMono.variable}`}>
      <body>
        <Toaster position="top-right" />
        <header className="shopfront">
          <div className="shopfront-inner">
            <span className="shopfront-mark">Keyloop Service Dept.</span>
            <span className="shopfront-status">
              <span className="dot" aria-hidden="true" />
              Booking open
            </span>
          </div>
        </header>
        <main className="page">
          <h1>Unified Service Scheduler</h1>
          <p className="subtitle">
            Pick a service, a vehicle, and a time slot. Leave the technician on
            &ldquo;no preference&rdquo; and we assign a qualified, available
            one — or choose your own. Every slot shown is live: book the same
            one twice and the second request gets turned away.
          </p>
          <SiteNav />
          <div className="page-shell">{children}</div>
        </main>
      </body>
    </html>
  );
}
