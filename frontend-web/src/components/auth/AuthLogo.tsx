import { usePublicBranding } from '../../hooks/usePublicBranding';

const INNOVACAR_LOGO_URL = '/brand/innovacar-logo.png';

/**
 * Logo-only branding mark for public auth pages (login, register, password
 * reset, OTP/email verification). Deliberately renders no text alongside the
 * image — see the Innovacar auth-branding cleanup: the wordmark next to the
 * logo was redundant and got removed everywhere this appears.
 */
export default function AuthLogo() {
  const branding = usePublicBranding();
  const resolvedLogo = (branding?.found && branding.logoUrl) || INNOVACAR_LOGO_URL;

  return (
    <div className="flex justify-center mb-10">
      <div className="flex h-[72px] w-[72px] items-center justify-center overflow-hidden rounded-2xl bg-white shadow-lg shadow-accent-400/20 sm:h-20 sm:w-20 md:h-24 md:w-24">
        <img
          src={resolvedLogo}
          alt="Innovacar"
          className="h-full w-full object-contain p-1"
          onError={(e) => {
            if (e.currentTarget.src !== window.location.origin + INNOVACAR_LOGO_URL) {
              e.currentTarget.src = INNOVACAR_LOGO_URL;
            }
          }}
        />
      </div>
    </div>
  );
}
