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
    <html
      lang="en"
      className={`${displayStencil.variable} ${bodySans.variable} ${dataMono.variable} h-full max-w-[100vw] overflow-x-hidden dark:[color-scheme:dark]`}
    >
      <body className="flex min-h-full max-w-[100vw] flex-col overflow-x-hidden bg-bg font-sans text-ink antialiased">
        <Toaster position="top-right" />
        <header className="bg-ink text-panel">
          <div className="mx-auto flex max-w-[960px] items-baseline justify-between gap-4 px-6 py-4">
            <span className="font-heading text-[1.15rem] font-bold tracking-[0.06em] uppercase">
              Keyloop Service Dept.
            </span>
            <span className="inline-flex items-center gap-1.5 font-mono text-[0.72rem] tracking-[0.08em] whitespace-nowrap text-[#c9c3b4] uppercase">
              <span
                aria-hidden="true"
                className="size-2 rounded-full bg-brand shadow-[0_0_0_3px_color-mix(in_srgb,var(--brand)_25%,transparent)]"
              />
              Booking open
            </span>
          </div>
        </header>
        <main className="mx-auto max-w-[960px] px-6 pt-10 pb-16">
          <h1 className="mb-2.5 font-heading text-[clamp(2.1rem,5vw,3.1rem)] leading-[0.98] font-extrabold tracking-[0.01em] uppercase">
            Unified Service Scheduler
          </h1>
          <p className="mb-9 max-w-[60ch] text-muted-text leading-[1.55]">
            Pick a service, a vehicle, and a time slot. Leave the technician on
            &ldquo;no preference&rdquo; and we assign a qualified, available
            one — or choose your own. Every slot shown is live: book the same
            one twice and the second request gets turned away.
          </p>
          <SiteNav />
          <div className="max-w-[460px]">{children}</div>
        </main>
      </body>
    </html>
  );
}
