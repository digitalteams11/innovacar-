import { useEffect, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Download, Loader2, Printer, AlertCircle } from 'lucide-react';
import Modal from '../Modal';
import api from '../../api/axios';
import { logDevError, toFriendlyError } from '../../lib/errorMessages';

interface InvoicePdfPreviewModalProps {
  invoiceId: number;
  invoiceNumber: string;
  lang: string;
  isOpen: boolean;
  onClose: () => void;
  onDownload: () => void;
  downloading?: boolean;
}

/**
 * Lightweight PDF preview: fetches GET /invoices/{id}/pdf?mode=inline into a
 * blob URL and renders it in an iframe sized to fill the modal. Print uses
 * the iframe's own contentWindow.print() so it prints the PDF, not the
 * dashboard page behind it. The blob URL is revoked on close/unmount so we
 * never leak it.
 */
export default function InvoicePdfPreviewModal({
  invoiceId, invoiceNumber, lang, isOpen, onClose, onDownload, downloading,
}: InvoicePdfPreviewModalProps) {
  const { t } = useTranslation();
  const [blobUrl, setBlobUrl] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const iframeRef = useRef<HTMLIFrameElement>(null);
  const blobUrlRef = useRef<string | null>(null);

  const revoke = () => {
    if (blobUrlRef.current) {
      URL.revokeObjectURL(blobUrlRef.current);
      blobUrlRef.current = null;
    }
  };

  useEffect(() => {
    if (!isOpen) return;
    let cancelled = false;
    setError(null);
    setLoading(true);
    setBlobUrl(null);
    revoke();

    api.get(`/invoices/${invoiceId}/pdf`, {
      responseType: 'blob',
      params: { mode: 'inline', lang },
    }).then((response) => {
      if (cancelled) return;
      const blob = new Blob([response.data], { type: 'application/pdf' });
      const url = URL.createObjectURL(blob);
      blobUrlRef.current = url;
      setBlobUrl(url);
    }).catch((err) => {
      if (cancelled) return;
      logDevError('InvoicePdfPreview', err);
      setError(toFriendlyError(err, t('invoices.previewFailed', 'Unable to load this invoice PDF.')).message);
    }).finally(() => {
      if (!cancelled) setLoading(false);
    });

    return () => {
      cancelled = true;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [isOpen, invoiceId, lang]);

  useEffect(() => () => revoke(), []);

  const handleClose = () => {
    revoke();
    setBlobUrl(null);
    onClose();
  };

  const handlePrint = () => {
    iframeRef.current?.contentWindow?.print();
  };

  return (
    <Modal
      isOpen={isOpen}
      onClose={handleClose}
      title={t('invoices.previewTitle', { number: invoiceNumber, defaultValue: `Facture ${invoiceNumber}` })}
      maxWidth="max-w-4xl"
      footer={
        <div className="flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
          <button
            type="button"
            onClick={handleClose}
            className="min-h-11 flex-1 rounded-xl px-4 text-sm font-semibold transition-all sm:flex-none"
            style={{ background: 'var(--bg-hover)', color: 'var(--text-secondary)' }}
          >
            {t('actions.close', 'Fermer')}
          </button>
          <button
            type="button"
            onClick={handlePrint}
            disabled={!blobUrl}
            className="flex min-h-11 flex-1 items-center justify-center gap-2 rounded-xl px-4 text-sm font-semibold transition-all disabled:cursor-not-allowed disabled:opacity-50 sm:flex-none"
            style={{ background: 'var(--bg-hover)', color: 'var(--text-secondary)' }}
          >
            <Printer size={16} /> {t('invoices.print', 'Imprimer')}
          </button>
          <button
            type="button"
            onClick={onDownload}
            disabled={downloading}
            className="flex min-h-11 flex-1 items-center justify-center gap-2 rounded-xl px-4 text-sm font-semibold text-white transition-all disabled:cursor-not-allowed disabled:opacity-60 sm:flex-none"
            style={{ background: 'var(--brand-primary)' }}
          >
            {downloading ? <Loader2 size={16} className="animate-spin" /> : <Download size={16} />}
            {t('invoices.download', 'Télécharger')}
          </button>
        </div>
      }
    >
      <div className="flex h-[65vh] min-h-[320px] w-full items-center justify-center overflow-hidden rounded-xl border border-[var(--border-subtle)] bg-[var(--bg-hover)]">
        {loading && <Loader2 size={28} className="animate-spin text-brand-500" />}
        {!loading && error && (
          <div className="flex flex-col items-center gap-2 p-6 text-center text-sm text-[var(--text-secondary)]">
            <AlertCircle size={22} className="text-danger-500" />
            {error}
          </div>
        )}
        {!loading && !error && blobUrl && (
          <iframe
            ref={iframeRef}
            src={blobUrl}
            title={t('invoices.previewTitle', { number: invoiceNumber, defaultValue: `Facture ${invoiceNumber}` }) as string}
            className="h-full w-full border-0"
          />
        )}
      </div>
    </Modal>
  );
}
