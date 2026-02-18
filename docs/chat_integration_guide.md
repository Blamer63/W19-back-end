# Chat API Integration Guide

This document outlines the REST API endpoints and data structures for the Chat feature.

## Base URL
`http://localhost:8081/api`

---

## 1. Data Structures

### ChatRequest
```typescript
interface ChatRequest {
  recipient_id: string; // UUID of the recipient
  content: string;      // Message content
}
```

### MessageResponse
```typescript
interface MessageResponse {
  id: string;              // UUID of the message
  conversation_id: string; // UUID of the conversation
  sender_id: string;       // UUID of the sender
  content: string;         // Message text
  is_read: boolean;
  created_at: string;      // ISO-8601
}
```

### ConversationResponse
```typescript
interface ConversationResponse {
  id: string;
  recipient: {
    id: string;
    display_name: string;
    avatar_url: string;
  };
  last_message_preview: string;
  last_message_at: string;
}
```

---

## 2. Endpoints

### List Conversations
**Endpoint:** `GET /conversations`  
**Auth:** Required (JWT)  
**Query Params:** `page`, `size`  

**Example:**
```typescript
const getConversations = async (page = 0) => {
  const response = await fetch(`${API_BASE}/conversations?page=${page}&size=20`, {
    headers: { 'Authorization': `Bearer ${token}` }
  });
  return await response.json();
};
```

### Start Conversation / Send First Message
**Endpoint:** `POST /conversations`  
**Auth:** Required (JWT)  
**Request Body:** `ChatRequest`  

**Example:**
```typescript
const startChat = async (recipientId: string, text: string) => {
  const response = await fetch(`${API_BASE}/conversations`, {
    method: 'POST',
    headers: { 
      'Authorization': `Bearer ${token}`,
      'Content-Type': 'application/json'
    },
    body: JSON.stringify({ recipient_id: recipientId, content: text })
  });
  return await response.json();
};
```

### Send Message (Existing Conversation)
**Endpoint:** `POST /conversations/{conversation_id}/messages`  
**Auth:** Required (JWT)  
**Request Body:** `json { "content": "Hello again" }`

### Retrieve Message history
**Endpoint:** `GET /conversations/{conversation_id}/messages`  
**Auth:** Required (JWT)  
**Query Params:** `page`, `size` (sorted by `created_at DESC`)

---

## 3. Real-time (WebSockets)
*Currently in development. Use long-polling or refresh-on-foreground for now.*
WebSocket Endpoint: `/ws-chat`

---

## 4. Example React usage (Snippet)

```typescript
const sendMessage = async (convId: string, content: string) => {
  try {
    const response = await fetch(`${API_BASE}/conversations/${convId}/messages`, {
      method: 'POST',
      headers: getAuthHeaders(),
      body: JSON.stringify({ content })
    });
    const newMessage = await response.json();
    setMessages(prev => [newMessage, ...prev]);
  } catch (error) {
    console.error("Failed to send message", error);
  }
};
```
