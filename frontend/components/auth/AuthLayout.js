import { IconShieldCheck, IconTimeline, IconUsersGroup } from '@tabler/icons-react';
import { BrandMark } from '@/components/ui/BrandMark';
import { PulseLine } from '@/components/ui/PulseLine';
import classes from './AuthLayout.module.css';

const FEATURES = [
  {
    icon: IconUsersGroup,
    title: 'One record, clinic to lab',
    text: 'Register once. Doctors, lab and front desk share the same patient.',
  },
  {
    icon: IconTimeline,
    title: 'Every sample, tracked live',
    text: 'From collection to verified report, with a full chain of custody.',
  },
  {
    icon: IconShieldCheck,
    title: 'Verified before released',
    text: 'No report leaves the lab without a pathologist’s sign-off.',
  },
];

/** Split-screen auth page: animated brand panel + focused form (Design.md §5.8). */
export function AuthLayout({ title, subtitle, children }) {
  return (
    <div className={classes.page}>
      <aside className={classes.panel} aria-hidden="true">
        <div className={classes.mesh}>
          <span className={classes.blob} />
          <span className={classes.blob} />
          <span className={classes.blob} />
        </div>
        <div className={classes.grain} />

        <BrandMark color="#ffffff" subtitle={<span style={{ color: 'rgb(233 251 247 / 0.6)' }}>Clinic &amp; Diagnostic Lab</span>} />

        <div>
          <h2 className={classes.headline}>
            Care, measured <em>precisely.</em>
          </h2>
          <p className={classes.lede}>
            Appointments, consultations, lab samples and verified reports — one calm, connected
            workspace for everyone in the clinic.
          </p>
          <div className={classes.pulse}>
            <PulseLine />
          </div>
          <ul className={classes.features}>
            {FEATURES.map(({ icon: Icon, title: featureTitle, text }) => (
              <li key={featureTitle}>
                <span className={classes.featureIcon}>
                  <Icon size={17} stroke={1.6} />
                </span>
                <span>
                  <strong>{featureTitle}</strong>
                  {text}
                </span>
              </li>
            ))}
          </ul>
        </div>

        <div className={classes.footer}>Clinic &amp; Diagnostic Lab Management System</div>
      </aside>

      <main id="main" className={classes.formSide}>
        <div className={classes.formInner}>
          <div className={classes.mobileBrand}>
            <BrandMark subtitle="Clinic & Diagnostic Lab" />
          </div>
          <h1 className={classes.title}>{title}</h1>
          {subtitle && (
            <p style={{ margin: '0 0 28px', color: 'var(--text-muted)', fontSize: 15 }}>{subtitle}</p>
          )}
          {children}
        </div>
      </main>
    </div>
  );
}
