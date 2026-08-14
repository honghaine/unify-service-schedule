import type { Metadata } from "next";
import { Big_Shoulders_Stencil, Archivo, IBM_Plex_Mono } from "next/font/google";
import "./globals.css";

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
      <body>{children}</body>
    </html>
  );
}
