export interface FaqEntry {
  q: string;
  a: string;
}

export interface DocumentationSection {
  heading: string;
  steps: string[];
}

export interface ModuleHelpContent {
  title: string;
  videoUrl: string | null;
  walkthrough: string[];
  documentation: {
    overview: string;
    keyActions: string[];
    workflow: DocumentationSection[];
    requiredFields: string[];
    commonErrors: string[];
    tips: string[];
  };
  faq: FaqEntry[];
}

const defaultWalkthrough = [
  'Open the module from the sidebar.',
  'Review the main data table or cards.',
  'Click the primary action button (usually top-right).',
  'Fill in the required fields in the form.',
  'Save and verify the result appears in the list.',
  'Use the filters/search bar to manage existing records.',
];

export const generalFaq: FaqEntry[] = [
  { q: 'Why do I see a 401 error?', a: 'Your session expired. Sign in again to continue.' },
  { q: 'Why do I see a 409 error?', a: 'The record already exists or conflicts with another booking (e.g. vehicle already reserved for those dates).' },
  { q: 'Why does the PDF not download?', a: 'Use the dedicated PDF button on the record. The app downloads it through a secure blob flow — pop-up blockers do not affect it.' },
  { q: 'Why is the reservations page empty?', a: 'Reservations must be created directly or generated from a contract. Use "New reservation" to create the first one.' },
  { q: 'Why does the QR code show an invalid link?', a: 'The QR token may be expired or it is the wrong QR type (signature vs inspection). Regenerate the QR code from the contract.' },
  { q: 'Why can\'t I edit some fields?', a: 'Some fields lock once a contract is signed or a payment is recorded, to preserve an accurate audit trail.' },
];

export const helpCenterContent: Record<string, ModuleHelpContent> = {
  dashboard: {
    title: 'Dashboard',
    videoUrl: null,
    walkthrough: [
      'Check the stat cards at the top: reservations today, active contracts, vehicles status, and revenue overview.',
      'Use the recommendation card to jump to your next setup action.',
      'Review the charts to spot trends in bookings and revenue.',
      'Click any stat card to drill into the related module.',
      'Use the date range selector to compare different periods.',
    ],
    documentation: {
      overview: 'The dashboard summarizes your agency activity using live data: reservations, contracts, fleet status, and revenue.',
      keyActions: ['Read stat cards', 'Open recommended next action', 'Drill into a module from a card'],
      workflow: [
        {
          heading: 'Understand the stat cards',
          steps: [
            'Reservations today: bookings starting or ending today.',
            'Active contracts: contracts currently in progress.',
            'Vehicles status: available vs rented vs maintenance.',
            'Revenue overview: payments collected over the selected period.',
          ],
        },
        {
          heading: 'Use the recommendation panel',
          steps: [
            'If your fleet, clients, reservations, or contracts are empty, a recommendation card suggests the next setup step.',
            'Click the card to go directly to the right module.',
          ],
        },
      ],
      requiredFields: [],
      commonErrors: ['Stats show zero because no data has been created yet for this period.'],
      tips: ['Use the Help Center on every page — its content changes to match the module you are viewing.'],
    },
    faq: [
      { q: 'Why are my stats showing zero?', a: 'No reservations, contracts, or payments exist yet for the selected period. Create your first vehicle and reservation to populate the dashboard.' },
      { q: 'How often does the dashboard refresh?', a: 'Data is fetched live each time you open or revisit the dashboard.' },
    ],
  },

  reservations: {
    title: 'Reservations',
    videoUrl: null,
    walkthrough: [
      'Click "New reservation".',
      'Select a client (or create one inline).',
      'Select an available vehicle for the desired dates.',
      'Set the pickup and return dates/times.',
      'Save the reservation.',
      'Convert the reservation into a contract when the client arrives.',
    ],
    documentation: {
      overview: 'Reservations connect a client and a vehicle for specific dates, and can later be converted into a contract.',
      keyActions: ['Create a reservation', 'Update dates/duration', 'Convert to a contract', 'Cancel a reservation'],
      workflow: [
        {
          heading: 'Create a reservation',
          steps: [
            'Click "New reservation".',
            'Choose an existing client or create a new one from the search field.',
            'Pick a vehicle — only vehicles available for the selected dates appear.',
            'Confirm pickup/return dates and pricing, then save.',
          ],
        },
        {
          heading: 'Update dates or duration',
          steps: [
            'Open the reservation and edit the dates.',
            'Availability is re-checked automatically to avoid conflicts.',
          ],
        },
        {
          heading: 'Link to a contract',
          steps: [
            'Open the reservation and click "Generate contract".',
            'Client and vehicle data is reused automatically — no duplicate entry.',
          ],
        },
      ],
      requiredFields: ['Client', 'Vehicle', 'Pickup date', 'Return date'],
      commonErrors: [
        '409 Conflict: the vehicle is already booked for an overlapping date range.',
        'Empty vehicle list: no vehicle is available for the selected dates — adjust the dates or add a vehicle.',
      ],
      tips: ['Reservations do not block the vehicle permanently — cancel one to free the vehicle immediately.'],
    },
    faq: [
      { q: 'How does vehicle availability work?', a: 'A vehicle is unavailable for a date range if it already has an overlapping reservation or active contract.' },
      { q: 'Can I edit a reservation after creating it?', a: 'Yes, as long as it has not yet been converted into a signed contract.' },
      { q: 'How do I turn a reservation into a contract?', a: 'Open the reservation and click "Generate contract" — client and vehicle details carry over automatically.' },
    ],
  },

  contracts: {
    title: 'Contracts',
    videoUrl: null,
    walkthrough: [
      'Click "New contract" or generate one from a reservation.',
      'Confirm client, vehicle, and pricing details.',
      'Apply the agency signature.',
      'Generate the client QR code for remote signing.',
      'Have the client sign from their phone.',
      'Download the signed PDF.',
      'Start the vehicle inspection at handover and return.',
    ],
    documentation: {
      overview: 'Contracts reuse reservation, client, and vehicle data, and support digital signatures, QR-based client signing, and PDF generation.',
      keyActions: ['Create a contract', 'Apply agency signature', 'Generate client QR', 'Download PDF', 'Run inspections'],
      workflow: [
        { heading: '1. Create a contract', steps: ['Generate from an existing reservation, or click "New contract" to start fresh.'] },
        { heading: '2. Link client and vehicle', steps: ['Confirm or change the linked client and vehicle before finalizing.'] },
        { heading: '3. Apply agency signature', steps: ['Use the signature pad or saved signature to sign as the agency.'] },
        { heading: '4. Generate client QR', steps: ['Click "Generate QR" to produce a secure, time-limited signing link.'] },
        { heading: '5. Client signs from phone', steps: ['The client scans the QR and signs on their own device — no app install needed.'] },
        { heading: '6. Download PDF', steps: ['Once both signatures are present, download the finalized contract PDF.'] },
        { heading: '7. Start vehicle inspection', steps: ['Run a handover inspection when the vehicle leaves, and a return inspection when it comes back.'] },
      ],
      requiredFields: ['Client', 'Vehicle', 'Rental dates', 'Pricing', 'Agency signature'],
      commonErrors: [
        'PDF not downloading: ensure both signatures are completed first.',
        'QR shows "invalid link": the token expired or the wrong QR type was scanned — regenerate it.',
      ],
      tips: ['Inspections recorded before and after the rental protect you in case of damage disputes.'],
    },
    faq: [
      { q: 'How do I apply the agency signature?', a: 'Open the contract and use the signature pad, or reuse a saved signature from agency settings.' },
      { q: 'How does the client sign remotely?', a: 'Generate a QR code from the contract; the client scans it with their phone and signs there directly.' },
      { q: 'Why does my QR code say invalid?', a: 'QR tokens expire after a set time, or the wrong QR type (signature vs inspection) was generated. Regenerate it from the contract.' },
      { q: 'How do I download the PDF?', a: 'Use the PDF button on the contract — it downloads through a secure blob flow once signatures are complete.' },
    ],
  },

  clients: {
    title: 'Clients',
    videoUrl: null,
    walkthrough: [
      'Click "New client".',
      'Fill in name, phone, CIN/passport, and contact details.',
      'Upload identity documents if required.',
      'Save the client profile.',
      'Use the client profile to view reservation, contract, and payment history.',
    ],
    documentation: {
      overview: 'Client profiles store identity, contact, and document information used across reservations, contracts, and payments.',
      keyActions: ['Create a client', 'Upload documents', 'View client history'],
      workflow: [
        { heading: 'Create a client', steps: ['Click "New client", fill required fields, and save.'] },
        { heading: 'Avoid duplicates', steps: ['Phone number and CIN/passport must be unique per agency — the form blocks duplicates with a clear error.'] },
        { heading: 'View history', steps: ['Open a client profile to see all linked reservations, contracts, and payments.'] },
      ],
      requiredFields: ['Full name', 'Phone number', 'CIN or passport number'],
      commonErrors: ['409 Conflict: a client with the same phone or CIN already exists — search before creating a new one.'],
      tips: ['Use the smart client search when creating a reservation to avoid duplicate profiles.'],
    },
    faq: [
      { q: 'Why can\'t I create a client with this phone number?', a: 'A client with that phone number already exists in your agency. Search for them instead of creating a duplicate.' },
      { q: 'What fields are required?', a: 'Full name, phone number, and a CIN or passport number are required to create a client.' },
      { q: 'Can two clients share the same CIN?', a: 'No — CIN/passport numbers must be unique per agency to prevent duplicate identities.' },
    ],
  },

  vehicles: {
    title: 'Vehicles',
    videoUrl: null,
    walkthrough: [
      'Click "Add vehicle".',
      'Fill in make, model, plate number, and category.',
      'Set the daily rate and availability status.',
      'Optionally link a GPS device.',
      'Save the vehicle.',
      'Track maintenance and availability from the vehicle list.',
    ],
    documentation: {
      overview: 'Vehicles are the core fleet inventory used by reservations and contracts. Each vehicle tracks status, maintenance, and optional GPS data.',
      keyActions: ['Add a vehicle', 'Update availability status', 'Track maintenance', 'Link GPS'],
      workflow: [
        { heading: 'Add a vehicle', steps: ['Click "Add vehicle", fill in details (make, model, plate, category, daily rate), and save.'] },
        { heading: 'Manage availability', steps: ['Status updates automatically based on active reservations/contracts, or set it manually for maintenance.'] },
        { heading: 'Maintenance and GPS', steps: ['Log maintenance entries from the vehicle profile.', 'Link a GPS device from GPS Settings to enable live tracking.'] },
      ],
      requiredFields: ['Make', 'Model', 'Plate number', 'Daily rate'],
      commonErrors: ['A vehicle marked "in maintenance" cannot be selected for new reservations until its status changes.'],
      tips: ['Keep plate numbers unique and accurate — they are used for QR-based inspections and contract documents.'],
    },
    faq: [
      { q: 'How does vehicle availability status work?', a: 'A vehicle is automatically marked unavailable while it has an active reservation or contract; you can also set it to maintenance manually.' },
      { q: 'How do I connect GPS tracking?', a: 'Go to GPS Settings, add your provider, then link the device to the vehicle profile.' },
      { q: 'How do I log maintenance?', a: 'Open the vehicle profile and add a maintenance entry with date, cost, and notes.' },
    ],
  },

  contractTemplates: {
    title: 'Contract Templates',
    videoUrl: null,
    walkthrough: [
      'Click "New template" or upload a scanned paper contract.',
      'Map each field (client name, dates, price, signature zone, etc.) onto the document.',
      'Save the field mapping.',
      'Set the template as default if it should be used automatically.',
      'Generate a contract PDF using the template to verify it.',
    ],
    documentation: {
      overview: 'Contract templates let you reuse your agency\'s existing paper contract layout by mapping dynamic fields onto a scanned document.',
      keyActions: ['Upload scanned paper', 'Map fields', 'Save mapping', 'Set default', 'Generate PDF using template'],
      workflow: [
        { heading: '1. Upload scanned paper', steps: ['Upload a scan or photo of your existing paper contract as the template background.'] },
        { heading: '2. Map fields', steps: ['Drag each dynamic field (client, vehicle, dates, price, signature) onto its position on the scanned page.'] },
        { heading: '3. Save mapping', steps: ['Save once every required field has a position — missing fields are highlighted.'] },
        { heading: '4. Set as default', steps: ['Mark the template as default so new contracts use it automatically.'] },
        { heading: '5. Generate PDF using template', steps: ['Create a test contract and download the PDF to confirm the layout looks correct.'] },
      ],
      requiredFields: ['Template name', 'Background scan', 'Field mapping for client, vehicle, dates, price, signature'],
      commonErrors: ['Generated PDF looks misaligned: re-check field coordinates in the mapping editor and re-save.'],
      tips: ['Keep one template marked default at a time — new contracts always use the default template.'],
    },
    faq: [
      { q: 'Can I use my own paper contract design?', a: 'Yes — upload a scan of your paper contract and map the dynamic fields onto it.' },
      { q: 'How do I set the default template?', a: 'Open the template and toggle "Set as default". Only one template can be default at a time.' },
      { q: 'Why does the generated PDF look wrong?', a: 'A field is likely mapped to the wrong position. Re-open the mapping editor, adjust coordinates, and save again.' },
    ],
  },

  settings: {
    title: 'Settings',
    videoUrl: null,
    walkthrough: [
      'Open Settings from the sidebar.',
      'Update your profile and upload an avatar.',
      'Update agency information (name, address, contact).',
      'Adjust appearance (theme, language).',
      'Save changes.',
    ],
    documentation: {
      overview: 'Settings covers your personal profile, agency identity, and appearance preferences.',
      keyActions: ['Update profile', 'Upload avatar', 'Update agency settings', 'Change appearance'],
      workflow: [
        { heading: 'Update your profile', steps: ['Edit your name and contact info, then save.'] },
        { heading: 'Upload an avatar', steps: ['Click the avatar image and upload a new photo.'] },
        { heading: 'Agency settings', steps: ['Update agency name, logo, address, currency, timezone, and tax rate.'] },
        { heading: 'Appearance', steps: ['Switch between light/dark/auto theme and change the interface language.'] },
      ],
      requiredFields: [],
      commonErrors: ['Avatar upload fails if the image exceeds the size limit — use a smaller image.'],
      tips: ['Changes to language and theme apply immediately without reloading.'],
    },
    faq: [
      { q: 'How do I change the interface language?', a: 'Use the language switcher in Settings or the topbar — it instantly applies across the app.' },
      { q: 'How do I update agency information?', a: 'Go to Settings > Agency and edit name, logo, address, and contact details.' },
    ],
  },

  operationsCenter: {
    title: 'Operations Center',
    videoUrl: null,
    walkthrough: [
      'Click "New ticket" to report an issue.',
      'Describe the issue and set a priority.',
      'Submit the ticket.',
      'Track its status from the ticket list.',
      'Add follow-up comments until it is resolved.',
    ],
    documentation: {
      overview: 'The Operations Center is where you create and track support tickets for issues that need attention.',
      keyActions: ['Create a support ticket', 'Track ticket status', 'Add comments'],
      workflow: [
        { heading: 'Create a ticket', steps: ['Click "New ticket", describe the issue, set a priority, and submit.'] },
        { heading: 'Track status', steps: ['Open the ticket list to see open, in-progress, and resolved tickets.'] },
      ],
      requiredFields: ['Subject', 'Description', 'Priority'],
      commonErrors: [],
      tips: ['Add as much detail as possible (steps to reproduce, screenshots) to speed up resolution.'],
    },
    faq: [
      { q: 'How do I check my ticket status?', a: 'Open Operations Center and find your ticket in the list — its current status is shown next to it.' },
      { q: 'Can I add more information after submitting?', a: 'Yes, open the ticket and add a comment at any time.' },
    ],
  },

  general: {
    title: 'RentCar Platform',
    videoUrl: null,
    walkthrough: defaultWalkthrough,
    documentation: {
      overview: 'RentCar helps you manage your rental agency: vehicles, clients, reservations, contracts, and payments in one place.',
      keyActions: ['Navigate using the sidebar', 'Use the Help Center on any page for module-specific guidance'],
      workflow: [
        { heading: 'Get started', steps: ['Complete your agency setup from the onboarding wizard.', 'Add your first vehicle and client.', 'Create a reservation and convert it to a contract.'] },
      ],
      requiredFields: [],
      commonErrors: [],
      tips: ['Open the Help Center on any page — its content adapts to the module you are viewing.'],
    },
    faq: generalFaq,
  },
};

export function getModuleHelpContent(moduleKey: string | undefined): ModuleHelpContent {
  const content = (moduleKey && helpCenterContent[moduleKey]) || helpCenterContent.general;
  return {
    ...content,
    faq: content === helpCenterContent.general ? content.faq : [...content.faq, ...generalFaq],
  };
}
