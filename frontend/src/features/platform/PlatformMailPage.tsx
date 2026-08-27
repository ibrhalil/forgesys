import { useState } from 'react';
import { LuEye, LuSend } from 'react-icons/lu';
import { Page } from '../../components/Page';
import { Badge } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { SelectInput } from '../../components/ui/SelectInput';
import { Spinner } from '../../components/ui/Spinner';
import { TextField } from '../../components/ui/Field';
import { DetailField, DetailPanel } from '../../components/detail/DetailPanel';
import { useT } from '../../lib/i18n';
import { useMailPreview, useMailTestSend, usePlatformMailInfo } from './hooks';
import type { MailChannel, PlatformMailSampleData } from './types';

// Mirror of the backend PlatformMailTestService sample defaults (K-51) — the form
// prefills exactly what a send with blank fields would use.
const DEFAULT_FIRST_NAME = 'Test';
const DEFAULT_ORGANIZATION_NAME = 'ForgeSys Test';
const DEFAULT_ACTION_URL = 'https://mail-test.invalid/verify?token=mail-test-token';
const DEFAULT_EXPIRES_IN_HOURS = 24;

const CHANNEL_TONES: Record<MailChannel, 'green' | 'warning' | 'muted'> = {
  SMTP: 'green',
  LOG: 'warning',
  IN_MEMORY: 'muted',
};

const CHANNEL_LABEL_KEYS = {
  SMTP: 'platform.mail.channel.SMTP',
  LOG: 'platform.mail.channel.LOG',
  IN_MEMORY: 'platform.mail.channel.IN_MEMORY',
} as const;

/**
 * K-51 platform mail testing: shows what the active mail channel actually is,
 * renders templates with prefilled+editable sample data (no send) and fires a
 * real test mail through the profile's sender.
 */
export function PlatformMailPage() {
  const { t } = useT();
  const { data: info, isLoading } = usePlatformMailInfo();
  const preview = useMailPreview();
  const testSend = useMailTestSend();

  const [recipient, setRecipient] = useState('');
  const [template, setTemplate] = useState('TENANT_VERIFY');
  const [languageOverride, setLanguageOverride] = useState<'tr' | 'en' | null>(null);
  const [firstName, setFirstName] = useState(DEFAULT_FIRST_NAME);
  const [organizationName, setOrganizationName] = useState(DEFAULT_ORGANIZATION_NAME);
  const [actionUrl, setActionUrl] = useState(DEFAULT_ACTION_URL);
  const [expiresInHours, setExpiresInHours] = useState(String(DEFAULT_EXPIRES_IN_HOURS));

  const language = languageOverride ?? (info?.defaultLanguage === 'en' ? 'en' : 'tr');

  const templateOptions = (info?.templates ?? []).map((tpl) => ({
    value: tpl.name,
    label: language === 'en' ? tpl.subjectEn : tpl.subjectTr,
  }));
  const languageOptions = [
    { value: 'tr' as const, label: 'Türkçe' },
    { value: 'en' as const, label: 'English' },
  ];

  const sampleData: PlatformMailSampleData = {
    template,
    language,
    firstName: firstName || undefined,
    organizationName: organizationName || undefined,
    actionUrl: actionUrl || undefined,
    expiresInHours: expiresInHours ? Number(expiresInHours) : undefined,
  };

  if (isLoading) {
    return (
      <div className="flex items-center justify-center py-16">
        <Spinner size="lg" className="border-glass border-t-accent" />
      </div>
    );
  }

  return (
    <Page
      breadcrumb={[{ label: t('platform.console') }, { label: t('platform.nav.mail') }]}
      title={t('platform.nav.mail')}
      description={t('platform.mail.desc')}
    >
      <div className="flex flex-col gap-6">
        <DetailPanel title={t('platform.mail.config')}>
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
            <DetailField label={t('platform.mail.channel')}>
              {info && <Badge tone={CHANNEL_TONES[info.channel]}>{t(CHANNEL_LABEL_KEYS[info.channel])}</Badge>}
            </DetailField>
            <DetailField label={t('platform.mail.from')}>
              <span className="break-all text-sm text-main">{info?.from ?? '—'}</span>
            </DetailField>
            <DetailField label={t('platform.mail.defaultLanguage')}>
              <span className="text-sm uppercase text-main">{info?.defaultLanguage ?? '—'}</span>
            </DetailField>
            <DetailField label={t('platform.mail.templatesDir')}>
              <span className="break-all font-mono text-xs text-muted">
                {info?.templatesDir || t('platform.mail.templatesDirClasspath')}
              </span>
            </DetailField>
          </div>
          {info && info.channel !== 'SMTP' && (
            <p className="mt-4 rounded-lg border border-warning/30 bg-warning/10 px-3 py-2 text-sm text-warning">
              {t('platform.mail.channelWarning', { channel: info.channel })}
            </p>
          )}
        </DetailPanel>

        <div className="grid grid-cols-1 items-start gap-6 xl:grid-cols-2">
          <DetailPanel title={t('platform.mail.testSend')}>
            <div className="flex flex-col gap-4">
              <TextField
                id="mail-recipient"
                label={t('platform.mail.recipient')}
                placeholder={t('platform.mail.recipientPh')}
                type="email"
                value={recipient}
                onChange={(e) => setRecipient(e.target.value)}
              />
              <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
                <SelectInput
                  label={t('platform.mail.template')}
                  options={templateOptions}
                  value={templateOptions.find((o) => o.value === template) ?? null}
                  onChange={(o) => setTemplate((o as { value: string } | null)?.value ?? 'TENANT_VERIFY')}
                />
                <SelectInput
                  label={t('platform.mail.language')}
                  options={languageOptions}
                  value={languageOptions.find((o) => o.value === language) ?? null}
                  onChange={(o) => setLanguageOverride((o as { value: 'tr' | 'en' } | null)?.value ?? null)}
                />
              </div>

              <div className="mt-2 border-t border-glass pt-4">
                <p className="mb-3 text-xs font-semibold uppercase tracking-wider text-muted/70">
                  {t('platform.mail.sampleData')}
                </p>
                <div className="flex flex-col gap-4">
                  <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
                    <TextField
                      id="mail-first-name"
                      label={t('platform.mail.firstName')}
                      value={firstName}
                      onChange={(e) => setFirstName(e.target.value)}
                    />
                    <TextField
                      id="mail-organization-name"
                      label={t('platform.mail.organizationName')}
                      value={organizationName}
                      onChange={(e) => setOrganizationName(e.target.value)}
                    />
                  </div>
                  <TextField
                    id="mail-action-url"
                    label={t('platform.mail.actionUrl')}
                    value={actionUrl}
                    onChange={(e) => setActionUrl(e.target.value)}
                  />
                  <TextField
                    id="mail-expires-in-hours"
                    label={t('platform.mail.expiresInHours')}
                    type="number"
                    min={1}
                    value={expiresInHours}
                    onChange={(e) => setExpiresInHours(e.target.value)}
                  />
                </div>
              </div>

              <div className="mt-4 flex justify-end gap-3">
                <Button
                  variant="secondary"
                  loading={preview.isPending}
                  onClick={() => preview.mutate(sampleData)}
                >
                  {!preview.isPending && <LuEye size={16} />}
                  {preview.isPending ? t('platform.mail.previewing') : t('platform.mail.preview')}
                </Button>
                <Button
                  variant="primary"
                  disabled={!recipient.trim()}
                  loading={testSend.isPending}
                  onClick={() => testSend.mutate({ ...sampleData, recipient: recipient.trim() })}
                >
                  {!testSend.isPending && <LuSend size={16} />}
                  {testSend.isPending ? t('platform.mail.sending') : t('platform.mail.send')}
                </Button>
              </div>
            </div>
          </DetailPanel>

          <DetailPanel title={t('platform.mail.previewTitle')}>
            {preview.isPending && (
              <div className="flex items-center justify-center py-16">
                <Spinner className="border-glass border-t-accent" />
              </div>
            )}
            {!preview.isPending && !preview.data && (
              <p className="py-16 text-center text-sm text-muted">{t('platform.mail.previewEmpty')}</p>
            )}
            {preview.data && (
              <div className="flex flex-col gap-3">
                <DetailField label={t('platform.mail.previewSubject')}>
                  <span className="text-sm font-medium text-main">{preview.data.subject}</span>
                </DetailField>
                <iframe
                  title={t('platform.mail.previewTitle')}
                  srcDoc={preview.data.bodyHtml}
                  sandbox=""
                  className="h-[520px] w-full rounded-lg border border-glass bg-surface"
                />
              </div>
            )}
          </DetailPanel>
        </div>
      </div>
    </Page>
  );
}
