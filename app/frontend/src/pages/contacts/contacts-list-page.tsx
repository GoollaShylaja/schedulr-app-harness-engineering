import { useEffect, useState } from "react";
import { Button } from "../../components/ui/button";
import { Modal } from "../../components/ui/modal";
import { Pagination } from "../../components/ui/pagination";
import * as contactsApi from "../../lib/api/contacts";
import { ApiError } from "../../lib/api-client";
import type { ContactResponse } from "../../lib/types";
import { ContactForm } from "./contact-form";

export function ContactsListPage() {
  const [contacts, setContacts] = useState<ContactResponse[]>([]);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [isCreating, setIsCreating] = useState(false);
  const [editing, setEditing] = useState<ContactResponse | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  async function load() {
    setIsLoading(true);
    setError(null);
    try {
      const result = await contactsApi.listContacts({ page, size: 20 });
      setContacts(result.content);
      setTotalPages(result.totalPages);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not load contacts");
    } finally {
      setIsLoading(false);
    }
  }

  useEffect(() => {
    load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [page]);

  async function handleCreate(body: Parameters<typeof contactsApi.createContact>[0]) {
    await contactsApi.createContact(body);
    setIsCreating(false);
    setPage(0);
    await load();
  }

  async function handleUpdate(body: Parameters<typeof contactsApi.createContact>[0]) {
    if (!editing) return;
    await contactsApi.updateContact(editing.id, body);
    setEditing(null);
    await load();
  }

  async function handleDelete(id: string) {
    await contactsApi.deleteContact(id);
    await load();
  }

  return (
    <div className="page">
      <div className="page-header">
        <h1>Contacts</h1>
        <Button onClick={() => setIsCreating(true)}>New Contact</Button>
      </div>

      {error && <p className="form-error">{error}</p>}

      {isLoading ? (
        <div className="page-loading">Loading…</div>
      ) : (
        <table>
          <thead>
            <tr>
              <th>Name</th>
              <th>Email</th>
              <th>Company</th>
              <th>Stage</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            {contacts.map((contact) => (
              <tr key={contact.id}>
                <td>{contact.name}</td>
                <td>{contact.email}</td>
                <td>{contact.company}</td>
                <td>
                  <span className="badge">{contact.stage}</span>
                </td>
                <td style={{ display: "flex", gap: "0.5rem" }}>
                  <Button variant="secondary" onClick={() => setEditing(contact)}>
                    Edit
                  </Button>
                  <Button variant="danger" onClick={() => handleDelete(contact.id)}>
                    Delete
                  </Button>
                </td>
              </tr>
            ))}
            {contacts.length === 0 && (
              <tr>
                <td colSpan={5}>No contacts found.</td>
              </tr>
            )}
          </tbody>
        </table>
      )}

      <Pagination page={page} totalPages={totalPages} onPageChange={setPage} />

      {isCreating && (
        <Modal title="New Contact" onClose={() => setIsCreating(false)}>
          <ContactForm onSubmit={handleCreate} onCancel={() => setIsCreating(false)} />
        </Modal>
      )}

      {editing && (
        <Modal title="Edit Contact" onClose={() => setEditing(null)}>
          <ContactForm initial={editing} onSubmit={handleUpdate} onCancel={() => setEditing(null)} />
        </Modal>
      )}
    </div>
  );
}
