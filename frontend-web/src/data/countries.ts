/**
 * Countries the public client-information form lets a client pick from.
 * Kept as plain data (not inline in the form component) so it stays lazy
 * -loadable and easy to extend without touching component code. Names are
 * translated for the three languages the form supports (en/fr/ar); country
 * codes are ISO 3166-1 alpha-2 and are what actually gets submitted/stored
 * — the translated label is display-only.
 */
export interface CountryOption {
  code: string;
  en: string;
  fr: string;
  ar: string;
}

export const COUNTRIES: CountryOption[] = [
  { code: 'MA', en: 'Morocco', fr: 'Maroc', ar: 'المغرب' },
  { code: 'DZ', en: 'Algeria', fr: 'Algérie', ar: 'الجزائر' },
  { code: 'TN', en: 'Tunisia', fr: 'Tunisie', ar: 'تونس' },
  { code: 'LY', en: 'Libya', fr: 'Libye', ar: 'ليبيا' },
  { code: 'EG', en: 'Egypt', fr: 'Égypte', ar: 'مصر' },
  { code: 'MR', en: 'Mauritania', fr: 'Mauritanie', ar: 'موريتانيا' },
  { code: 'FR', en: 'France', fr: 'France', ar: 'فرنسا' },
  { code: 'ES', en: 'Spain', fr: 'Espagne', ar: 'إسبانيا' },
  { code: 'BE', en: 'Belgium', fr: 'Belgique', ar: 'بلجيكا' },
  { code: 'NL', en: 'Netherlands', fr: 'Pays-Bas', ar: 'هولندا' },
  { code: 'DE', en: 'Germany', fr: 'Allemagne', ar: 'ألمانيا' },
  { code: 'IT', en: 'Italy', fr: 'Italie', ar: 'إيطاليا' },
  { code: 'PT', en: 'Portugal', fr: 'Portugal', ar: 'البرتغال' },
  { code: 'CH', en: 'Switzerland', fr: 'Suisse', ar: 'سويسرا' },
  { code: 'GB', en: 'United Kingdom', fr: 'Royaume-Uni', ar: 'المملكة المتحدة' },
  { code: 'IE', en: 'Ireland', fr: 'Irlande', ar: 'أيرلندا' },
  { code: 'LU', en: 'Luxembourg', fr: 'Luxembourg', ar: 'لوكسمبورغ' },
  { code: 'SE', en: 'Sweden', fr: 'Suède', ar: 'السويد' },
  { code: 'NO', en: 'Norway', fr: 'Norvège', ar: 'النرويج' },
  { code: 'DK', en: 'Denmark', fr: 'Danemark', ar: 'الدنمارك' },
  { code: 'PL', en: 'Poland', fr: 'Pologne', ar: 'بولندا' },
  { code: 'AT', en: 'Austria', fr: 'Autriche', ar: 'النمسا' },
  { code: 'GR', en: 'Greece', fr: 'Grèce', ar: 'اليونان' },
  { code: 'TR', en: 'Turkey', fr: 'Turquie', ar: 'تركيا' },
  { code: 'US', en: 'United States', fr: 'États-Unis', ar: 'الولايات المتحدة' },
  { code: 'CA', en: 'Canada', fr: 'Canada', ar: 'كندا' },
  { code: 'SA', en: 'Saudi Arabia', fr: 'Arabie saoudite', ar: 'المملكة العربية السعودية' },
  { code: 'AE', en: 'United Arab Emirates', fr: 'Émirats arabes unis', ar: 'الإمارات العربية المتحدة' },
  { code: 'QA', en: 'Qatar', fr: 'Qatar', ar: 'قطر' },
  { code: 'KW', en: 'Kuwait', fr: 'Koweït', ar: 'الكويت' },
  { code: 'BH', en: 'Bahrain', fr: 'Bahreïn', ar: 'البحرين' },
  { code: 'OM', en: 'Oman', fr: 'Oman', ar: 'عُمان' },
  { code: 'JO', en: 'Jordan', fr: 'Jordanie', ar: 'الأردن' },
  { code: 'LB', en: 'Lebanon', fr: 'Liban', ar: 'لبنان' },
  { code: 'SN', en: 'Senegal', fr: 'Sénégal', ar: 'السنغال' },
  { code: 'CI', en: "Côte d'Ivoire", fr: "Côte d'Ivoire", ar: 'ساحل العاج' },
  { code: 'ML', en: 'Mali', fr: 'Mali', ar: 'مالي' },
  { code: 'CN', en: 'China', fr: 'Chine', ar: 'الصين' },
  { code: 'IN', en: 'India', fr: 'Inde', ar: 'الهند' },
  { code: 'RU', en: 'Russia', fr: 'Russie', ar: 'روسيا' },
  { code: 'BR', en: 'Brazil', fr: 'Brésil', ar: 'البرازيل' },
  { code: 'AU', en: 'Australia', fr: 'Australie', ar: 'أستراليا' },
];

export function countryLabel(country: CountryOption, lang: string): string {
  if (lang === 'ar') return country.ar;
  if (lang === 'fr') return country.fr;
  return country.en;
}

export function findCountry(code: string | undefined | null): CountryOption | undefined {
  if (!code) return undefined;
  return COUNTRIES.find((c) => c.code === code.toUpperCase());
}
