import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { superAdminApi } from '../../api/superAdminApi';
import { Edit2, Plus, Trash2 } from 'lucide-react';
import { PageHeader, DataTable, Badge, Modal, FormField, TextInput, TextArea } from '../../components/superadmin';
import { useToast } from '../../context/ToastContext';

const emptyForm = { id: null as number | null, slug: '', title: '', category: '', summary: '', content: '', published: true, isFaq: false };

export default function SuperAdminHelpArticles() {
  const { t } = useTranslation();
  const { showToast } = useToast();
  const [articles, setArticles] = useState<any[]>([]);
  const [loading, setLoading] = useState(true);
  const [showModal, setShowModal] = useState(false);
  const [form, setForm] = useState(emptyForm);

  useEffect(() => {
    fetchArticles();
  }, []);

  const fetchArticles = async () => {
    setLoading(true);
    try {
      const res = await superAdminApi.getHelpArticles();
      setArticles(res.data);
    } catch (err) {
      console.error(err);
      showToast('Unable to load help articles. Please try again later.', 'error');
    } finally {
      setLoading(false);
    }
  };

  const openCreate = () => {
    setForm(emptyForm);
    setShowModal(true);
  };

  const openEdit = (row: any) => {
    setForm({
      id: row.id,
      slug: row.slug || '',
      title: row.title || '',
      category: row.category || '',
      summary: row.summary || '',
      content: row.content || '',
      published: !!row.published,
      isFaq: !!row.isFaq,
    });
    setShowModal(true);
  };

  const handleSave = async () => {
    if (!form.title.trim() || !form.slug.trim()) {
      showToast('Title and slug are required.', 'error');
      return;
    }
    try {
      if (form.id) {
        await superAdminApi.updateHelpArticle(form.id, form);
      } else {
        await superAdminApi.createHelpArticle(form);
      }
      setShowModal(false);
      showToast('Article saved successfully', 'success');
      await fetchArticles();
    } catch (err: any) {
      console.error(err);
      showToast(err?.response?.data?.message || 'Unable to save this article.', 'error');
    }
  };

  const handleDelete = async (row: any) => {
    if (!window.confirm(`Delete article "${row.title}"?`)) return;
    try {
      await superAdminApi.deleteHelpArticle(row.id);
      showToast('Article deleted', 'success');
      await fetchArticles();
    } catch (err) {
      console.error(err);
      showToast('Unable to delete this article.', 'error');
    }
  };

  const columns = [
    {
      key: 'title',
      header: 'Article',
      render: (row: any) => (
        <div>
          <p className="text-sm font-semibold text-[#1e293b] dark:text-white">{row.title}</p>
          <p className="text-xs text-slate-500">/{row.slug}</p>
        </div>
      ),
    },
    { key: 'category', header: 'Category', render: (row: any) => <span className="text-sm text-slate-500">{row.category || '-'}</span> },
    { key: 'faq', header: 'FAQ', render: (row: any) => (row.isFaq ? <Badge variant="info">FAQ</Badge> : <span className="text-sm text-slate-400">-</span>) },
    { key: 'status', header: 'Status', render: (row: any) => <Badge variant={row.published ? 'success' : 'default'}>{row.published ? 'Published' : 'Draft'}</Badge> },
    {
      key: 'actions',
      header: t('superAdmin.common.actions'),
      align: 'right' as const,
      render: (row: any) => (
        <div className="flex items-center justify-end gap-1">
          <button onClick={() => openEdit(row)} className="p-2 hover:bg-slate-100 dark:hover:bg-white/5 rounded-lg transition-colors text-slate-400 hover:text-brand-600 dark:hover:text-brand-400" title={t('superAdmin.common.edit')}>
            <Edit2 size={16} />
          </button>
          <button onClick={() => handleDelete(row)} className="p-2 hover:bg-slate-100 dark:hover:bg-white/5 rounded-lg transition-colors text-slate-400 hover:text-danger-600" title={t('superAdmin.common.delete')}>
            <Trash2 size={16} />
          </button>
        </div>
      ),
    },
  ];

  return (
    <div className="space-y-6 animate-fade">
      <PageHeader title="Help Center" subtitle="Manage Help Center articles and FAQ entries — independent from Support Tickets and Contact Requests.">
        <button
          onClick={openCreate}
          className="flex items-center gap-2 bg-[#0a0f2c] hover:bg-[#0a0f2c]/90 text-white px-4 py-2.5 rounded-xl text-sm font-semibold transition-colors shadow-soft"
        >
          <Plus size={16} />
          <span className="hidden sm:inline">New Article</span>
        </button>
      </PageHeader>

      <DataTable columns={columns} data={articles} loading={loading} keyExtractor={(row) => row.id} emptyTitle="No help articles yet" />

      <Modal
        isOpen={showModal}
        onClose={() => setShowModal(false)}
        title={form.id ? 'Edit Article' : 'New Article'}
        size="lg"
        footer={
          <div className="flex gap-3">
            <button onClick={handleSave} className="flex-1 bg-[#0a0f2c] hover:bg-[#0a0f2c]/90 text-white py-2.5 rounded-xl text-sm font-semibold transition-colors">
              Save
            </button>
            <button onClick={() => setShowModal(false)} className="flex-1 bg-slate-100 dark:bg-white/5 hover:bg-slate-200 dark:hover:bg-white/10 text-[#1e293b] dark:text-white py-2.5 rounded-xl text-sm font-semibold transition-colors">
              Cancel
            </button>
          </div>
        }
      >
        <div className="space-y-4">
          <FormField label="Title" required>
            <TextInput value={form.title} onChange={(v) => setForm({ ...form, title: v })} placeholder="How to create a reservation" />
          </FormField>
          <FormField label="Slug" required>
            <TextInput value={form.slug} onChange={(v) => setForm({ ...form, slug: v })} placeholder="how-to-create-a-reservation" />
          </FormField>
          <FormField label="Category">
            <TextInput value={form.category} onChange={(v) => setForm({ ...form, category: v })} placeholder="Getting Started" />
          </FormField>
          <FormField label="Summary">
            <TextInput value={form.summary} onChange={(v) => setForm({ ...form, summary: v })} placeholder="Short one-line summary" />
          </FormField>
          <FormField label="Content" required>
            <TextArea value={form.content} onChange={(v) => setForm({ ...form, content: v })} placeholder="Full article content..." rows={8} />
          </FormField>
          <div className="flex items-center gap-6">
            <label className="flex items-center gap-2 text-sm text-[#1e293b] dark:text-white">
              <input type="checkbox" checked={form.published} onChange={(e) => setForm({ ...form, published: e.target.checked })} />
              Published
            </label>
            <label className="flex items-center gap-2 text-sm text-[#1e293b] dark:text-white">
              <input type="checkbox" checked={form.isFaq} onChange={(e) => setForm({ ...form, isFaq: e.target.checked })} />
              Show in FAQ section
            </label>
          </div>
        </div>
      </Modal>
    </div>
  );
}
