"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";

const LINKS = [
  { href: "/", label: "Book" },
  { href: "/lookup", label: "Look up" },
];

export default function SiteNav() {
  const pathname = usePathname();

  return (
    <nav className="mb-6 flex w-fit flex-row gap-1 rounded-lg bg-muted p-[3px]">
      {LINKS.map((link) => {
        const active = pathname === link.href;
        return (
          <Link
            key={link.href}
            href={link.href}
            className={`rounded-md px-3.5 py-1.5 font-heading text-[0.82rem] font-bold tracking-[0.04em] text-inherit no-underline uppercase transition-colors duration-150 ${
              active ? "bg-panel text-ink shadow-[0_1px_2px_color-mix(in_srgb,var(--ink)_12%,transparent)]" : "text-muted-foreground hover:text-ink"
            }`}
          >
            {link.label}
          </Link>
        );
      })}
    </nav>
  );
}
