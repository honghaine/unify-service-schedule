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
    <nav className="site-nav">
      {LINKS.map((link) => (
        <Link
          key={link.href}
          href={link.href}
          className={`site-nav-link${pathname === link.href ? " active" : ""}`}
        >
          {link.label}
        </Link>
      ))}
    </nav>
  );
}
